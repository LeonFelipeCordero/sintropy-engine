package com.ph.sintropyengine.broker.consumption.repository

import com.ph.sintropyengine.broker.consumption.model.EventLog
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.model.MessagePreStore
import com.ph.sintropyengine.jooq.generated.Tables
import com.ph.sintropyengine.jooq.generated.enums.MessageStatusType
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.JSONB

@ApplicationScoped
class MessageRepository(
    private val context: DSLContext
) {

    fun save(message: MessagePreStore): Message =
        context.insertInto(
            Tables.MESSAGES,
            Tables.MESSAGES.CHANNEL_ID,
            Tables.MESSAGES.PRODUCER_ID,
            Tables.MESSAGES.ROUTING_KEY,
            Tables.MESSAGES.MESSAGE,
            Tables.MESSAGES.HEADERS
        ).values(
            message.channelId,
            message.producerId,
            message.routingKey,
            JSONB.jsonb(message.message),
            JSONB.jsonb(message.headers)
        ).returning().fetchOneInto(Message::class.java)
            ?: throw IllegalStateException("Something went wrong persisting the message")

    fun pollFromStandardChannelByRoutingKey(channelId: UUID, routingKey: String, pollingCount: Int): List<Message> {
        val hash = hashCode(channelId, routingKey)
        val query = """
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

    fun pollFromFifoChannelByRoutingKey(channelId: UUID, routingKey: String, pollingCount: Int): List<Message> {
        val hash = hashCode(channelId, routingKey)
        val query = """
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
        return context.resultQuery(query, channelId, routingKey, channelId, routingKey, hash, pollingCount)
            .fetchInto(Message::class.java)
    }

    fun markAsFailed(messageId: UUID) {
        context.update(Tables.MESSAGES)
            .set(Tables.MESSAGES.STATUS, MessageStatusType.FAILED)
            .set(Tables.MESSAGES.UPDATED_AT, OffsetDateTime.now())
            .where(Tables.MESSAGES.MESSAGE_ID.eq(messageId))
            .execute()
    }

    fun dequeue(messageId: UUID) {
        context.delete(Tables.MESSAGES)
            .where(Tables.MESSAGES.MESSAGE_ID.eq(messageId))
            .execute()
    }

    fun findById(messageId: UUID): Message? =
        context.selectFrom(Tables.MESSAGES)
            .where(Tables.MESSAGES.MESSAGE_ID.eq(messageId))
            .fetchOneInto(Message::class.java)

    fun findAll(): List<Message> = context.selectFrom(Tables.MESSAGES).fetchInto(Message::class.java)
    fun findAllEventLog(): List<EventLog> = context.selectFrom(Tables.EVENT_LOG).fetchInto(EventLog::class.java)


    /**
     * Testing only
     */
    fun deleteAll() {
        context.delete(Tables.MESSAGES).execute()
        context.delete(Tables.EVENT_LOG).execute()
    }

    /**
     * Testing only
     */
    fun setMessageDeliveriesOutOfScope(messageId: UUID) {
        context.update(Tables.MESSAGES)
            .set(Tables.MESSAGES.STATUS, MessageStatusType.IN_FLIGHT)
            .set(Tables.MESSAGES.LAST_DELIVERED, OffsetDateTime.now().minusMinutes(16))
            .set(Tables.MESSAGES.DELIVERED_TIMES, 4)
            .set(Tables.MESSAGES.UPDATED_AT, OffsetDateTime.now())
            .where(Tables.MESSAGES.MESSAGE_ID.eq(messageId))
            .execute()
    }

    private fun hashCode(channelId: UUID, routingKey: String): Int {
        return (channelId.toString() + routingKey).hashCode()
    }
}