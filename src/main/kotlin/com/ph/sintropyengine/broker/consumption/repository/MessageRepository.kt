package com.ph.sintropyengine.broker.consumption.repository

import com.ph.sintropyengine.broker.consumption.model.DeadLetterMessage
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.model.MessageLog
import com.ph.sintropyengine.broker.consumption.model.MessagePreStore
import com.ph.sintropyengine.jooq.generated.Tables
import com.ph.sintropyengine.jooq.generated.enums.MessageStatusType
import jakarta.enterprise.context.ApplicationScoped
import org.jooq.DSLContext
import org.jooq.JSONB
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
class MessageRepository(
    private val context: DSLContext,
) {
    fun save(
        message: MessagePreStore,
        channelId: UUID,
        producerId: UUID,
    ): Message =
        context
            .insertInto(
                Tables.MESSAGES,
                Tables.MESSAGES.CHANNEL_ID,
                Tables.MESSAGES.PRODUCER_ID,
                Tables.MESSAGES.ROUTING_KEY,
                Tables.MESSAGES.MESSAGE,
                Tables.MESSAGES.HEADERS,
            ).values(
                channelId,
                producerId,
                message.routingKey,
                JSONB.jsonb(message.message),
                JSONB.jsonb(message.headers),
            ).returning()
            .fetchOneInto(Message::class.java)
            ?: throw IllegalStateException("Something went wrong persisting the message")

    fun pollFromStandardChannelByRoutingKey(
        channelId: UUID,
        routingKey: String,
        pollingCount: Int,
    ): List<Message> {
        val hash = hashCode(channelId, routingKey)
        val query =
            """
            with result as (select message_id
                            from messages
                            where channel_id = :channelId
                              and routing_key = :routingKey
                              and (status = 'READY' or
                                   (status = 'IN_FLIGHT' and
                                    last_delivered < now() - interval '15 minutes' and
                                    delivered_times < 4))
                              and pg_try_advisory_xact_lock(:hash)
                            order by timestamp
                            limit :pollingCount for update skip locked)
            update messages
            set status          = 'IN_FLIGHT',
                last_delivered  = now(),
                delivered_times = delivered_times + 1,
                updated_at      = now()
            from result
            where messages.message_id in (result.message_id)
            returning messages.*;
            ;
            """.trimIndent()
        return context.resultQuery(query, channelId, routingKey, hash, pollingCount).fetchInto(Message::class.java)
    }

    fun pollFromFifoChannelByRoutingKey(
        channelId: UUID,
        routingKey: String,
        pollingCount: Int,
    ): List<Message> {
        val hash = hashCode(channelId, routingKey)
        val query =
            """
            with result as (select message_id
                               from messages
                               where channel_id = :channelId
                                 and routing_key = :routingKey
                                 and status = 'READY'
                                 and not exists (select 1
                                                 from messages
                                                 where channel_id = :channelId
                                                   and routing_key = :routingKey
                                                   and status = 'IN_FLIGHT')
                                 and pg_try_advisory_xact_lock(:hash)
                               order by timestamp
                               limit :pollingCount for update skip locked)
               update messages
               set status          = 'IN_FLIGHT',
                   last_delivered  = now(),
                   delivered_times = delivered_times + 1,
                   updated_at      = now()
               from result
               where messages.message_id in (result.message_id)
               returning messages.*;
            ;
            """.trimIndent()
        return context
            .resultQuery(query, channelId, routingKey, channelId, routingKey, hash, pollingCount)
            .fetchInto(Message::class.java)
    }

    fun markAsFailed(messageId: UUID) {
        context
            .update(Tables.MESSAGES)
            .set(Tables.MESSAGES.STATUS, MessageStatusType.FAILED)
            .set(Tables.MESSAGES.UPDATED_AT, OffsetDateTime.now())
            .where(Tables.MESSAGES.MESSAGE_ID.eq(messageId))
            .execute()
    }

    fun markAsFailedBulk(messageIds: List<UUID>): List<Message> =
        context
            .update(Tables.MESSAGES)
            .set(Tables.MESSAGES.STATUS, MessageStatusType.FAILED)
            .set(Tables.MESSAGES.UPDATED_AT, OffsetDateTime.now())
            .where(Tables.MESSAGES.MESSAGE_ID.`in`(messageIds))
            .returning()
            .fetchInto(Message::class.java)

    fun dequeue(messageId: UUID) {
        context
            .delete(Tables.MESSAGES)
            .where(Tables.MESSAGES.MESSAGE_ID.eq(messageId))
            .execute()
    }

    fun dequeueBulk(messageIds: List<UUID>): List<Message> =
        context
            .delete(Tables.MESSAGES)
            .where(Tables.MESSAGES.MESSAGE_ID.`in`(messageIds))
            .returning()
            .fetchInto(Message::class.java)

    fun findByIds(messageIds: List<UUID>): List<Message> =
        context
            .selectFrom(Tables.MESSAGES)
            .where(Tables.MESSAGES.MESSAGE_ID.`in`(messageIds))
            .fetchInto(Message::class.java)

    fun reinsertFromDlq(dlqEntry: DeadLetterMessage): Message =
        context
            .insertInto(
                Tables.MESSAGES,
                Tables.MESSAGES.MESSAGE_ID,
                Tables.MESSAGES.TIMESTAMP,
                Tables.MESSAGES.CHANNEL_ID,
                Tables.MESSAGES.PRODUCER_ID,
                Tables.MESSAGES.ROUTING_KEY,
                Tables.MESSAGES.MESSAGE,
                Tables.MESSAGES.HEADERS,
                Tables.MESSAGES.STATUS,
                Tables.MESSAGES.LAST_DELIVERED,
                Tables.MESSAGES.DELIVERED_TIMES,
                Tables.MESSAGES.ORIGIN_MESSAGE_ID,
            ).values(
                dlqEntry.messageId,
                dlqEntry.timestamp,
                dlqEntry.channelId,
                dlqEntry.producerId,
                dlqEntry.routingKey,
                dlqEntry.message,
                dlqEntry.headers,
                MessageStatusType.READY,
                null,
                0,
                dlqEntry.originMessageId,
            ).returning()
            .fetchOneInto(Message::class.java)
            ?: throw IllegalStateException("Failed to reinsert message from DLQ")

    fun findById(messageId: UUID): Message? =
        context
            .selectFrom(Tables.MESSAGES)
            .where(Tables.MESSAGES.MESSAGE_ID.eq(messageId))
            .fetchOneInto(Message::class.java)

    fun findMessageLogById(messageId: UUID): MessageLog? =
        context
            .selectFrom(Tables.MESSAGE_LOG)
            .where(Tables.MESSAGE_LOG.MESSAGE_ID.eq(messageId))
            .fetchOneInto(MessageLog::class.java)

    fun findMessageLogFromToByChannelIdAndRoutingKey(
        channelId: UUID,
        routingKey: String,
        from: OffsetDateTime,
        to: OffsetDateTime?,
        pageSize: Int,
        page: Int,
    ): List<MessageLog> {
        val query =
            context
                .selectFrom(Tables.MESSAGE_LOG)
                .where(Tables.MESSAGE_LOG.CHANNEL_ID.eq(channelId))
                .and(Tables.MESSAGE_LOG.ROUTING_KEY.eq(routingKey))
                .and(Tables.MESSAGE_LOG.TIMESTAMP.greaterOrEqual(from))

        to?.run {
            query.and(Tables.MESSAGE_LOG.TIMESTAMP.lessOrEqual(to))
        }

        return query
            .limit(pageSize)
            .offset(page * pageSize)
            .fetchInto(MessageLog::class.java)
    }

    fun findAllMessagesByChannelIdAndRoutingKey(
        channelId: UUID,
        routingKey: String,
        pageSize: Int,
        page: Int,
    ) = context
        .selectFrom(Tables.MESSAGE_LOG)
        .where(Tables.MESSAGE_LOG.CHANNEL_ID.eq(channelId))
        .and(Tables.MESSAGE_LOG.ROUTING_KEY.eq(routingKey))
        .limit(pageSize)
        .offset(page * pageSize)
        .fetchInto(MessageLog::class.java)

    fun findAll(): List<Message> = context.selectFrom(Tables.MESSAGES).fetchInto(Message::class.java)

    fun findAllMessageLog(): List<MessageLog> = context.selectFrom(Tables.MESSAGE_LOG).fetchInto(MessageLog::class.java)

    /**
     * Testing only
     */
    fun deleteAll() {
        context.delete(Tables.MESSAGES).execute()
        context.delete(Tables.MESSAGE_LOG).execute()
    }

    /**
     * Testing only
     */
    fun setMessageDeliveriesOutOfScope(messageId: UUID) {
        context
            .update(Tables.MESSAGES)
            .set(Tables.MESSAGES.STATUS, MessageStatusType.IN_FLIGHT)
            .set(Tables.MESSAGES.LAST_DELIVERED, OffsetDateTime.now().minusMinutes(16))
            .set(Tables.MESSAGES.DELIVERED_TIMES, 4)
            .set(Tables.MESSAGES.UPDATED_AT, OffsetDateTime.now())
            .where(Tables.MESSAGES.MESSAGE_ID.eq(messageId))
            .execute()
    }

    private fun hashCode(
        channelId: UUID,
        routingKey: String,
    ): Int = (channelId.toString() + routingKey).hashCode()
}
