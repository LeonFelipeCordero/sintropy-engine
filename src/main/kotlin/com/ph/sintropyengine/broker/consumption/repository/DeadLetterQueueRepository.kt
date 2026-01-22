package com.ph.sintropyengine.broker.consumption.repository

import com.ph.sintropyengine.broker.consumption.model.DeadLetterMessage
import com.ph.sintropyengine.broker.consumption.model.MessagePreStore
import com.ph.sintropyengine.jooq.generated.Tables.DEAD_LETTER_QUEUE
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.JSONB

@ApplicationScoped
class DeadLetterQueueRepository(
    private val context: DSLContext,
) {
    fun findById(dlqEntryId: Long): DeadLetterMessage? =
        context
            .selectFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.DLQ_ENTRY_ID.eq(dlqEntryId))
            .fetchOneInto(DeadLetterMessage::class.java)

    fun findByMessageId(messageId: Long): DeadLetterMessage? =
        context
            .selectFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.MESSAGE_ID.eq(messageId))
            .orderBy(DEAD_LETTER_QUEUE.FAILED_AT.desc())
            .fetchOneInto(DeadLetterMessage::class.java)

    fun findByMessageUUID(messageUUID: UUID): DeadLetterMessage? =
        context
            .selectFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.MESSAGE_UUID.eq(messageUUID))
            .orderBy(DEAD_LETTER_QUEUE.FAILED_AT.desc())
            .fetchOneInto(DeadLetterMessage::class.java)

    fun findByChannelIdAndRoutingKey(
        channelId: Long,
        routingKey: String,
        pageSize: Int = 100,
        page: Int = 0,
    ): List<DeadLetterMessage> =
        context
            .selectFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.CHANNEL_ID.eq(channelId))
            .and(DEAD_LETTER_QUEUE.ROUTING_KEY.eq(routingKey))
            .orderBy(DEAD_LETTER_QUEUE.FAILED_AT.desc())
            .limit(pageSize)
            .offset(page * pageSize)
            .fetchInto(DeadLetterMessage::class.java)

    fun findAllByChannelIdAndRoutingKey(
        channelId: Long,
        routingKey: String,
    ): List<DeadLetterMessage> =
        context
            .selectFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.CHANNEL_ID.eq(channelId))
            .and(DEAD_LETTER_QUEUE.ROUTING_KEY.eq(routingKey))
            .orderBy(DEAD_LETTER_QUEUE.TIMESTAMP)
            .fetchInto(DeadLetterMessage::class.java)

    fun delete(dlqEntryId: Long) {
        context
            .deleteFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.DLQ_ENTRY_ID.eq(dlqEntryId))
            .execute()
    }

    fun deleteByIds(dlqEntryIds: List<Long>) {
        context
            .deleteFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.DLQ_ENTRY_ID.`in`(dlqEntryIds))
            .execute()
    }

    fun deleteByChannelIdAndRoutingKey(
        channelId: Long,
        routingKey: String,
    ) {
        context
            .deleteFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.CHANNEL_ID.eq(channelId))
            .and(DEAD_LETTER_QUEUE.ROUTING_KEY.eq(routingKey))
            .execute()
    }

    fun findAll(): List<DeadLetterMessage> =
        context
            .selectFrom(DEAD_LETTER_QUEUE)
            .fetchInto(DeadLetterMessage::class.java)

    fun deleteAll() {
        context.deleteFrom(DEAD_LETTER_QUEUE).execute()
    }

    fun save(
        message: MessagePreStore,
        channelId: Long,
        producerId: Long,
    ): DeadLetterMessage =
        context
            .insertInto(
                DEAD_LETTER_QUEUE,
                DEAD_LETTER_QUEUE.CHANNEL_ID,
                DEAD_LETTER_QUEUE.PRODUCER_ID,
                DEAD_LETTER_QUEUE.ROUTING_KEY,
                DEAD_LETTER_QUEUE.MESSAGE,
                DEAD_LETTER_QUEUE.HEADERS,
                DEAD_LETTER_QUEUE.ORIGIN_MESSAGE_ID,
                DEAD_LETTER_QUEUE.DELIVERED_TIMES,
            ).values(
                channelId,
                producerId,
                message.routingKey,
                JSONB.jsonb(message.message),
                JSONB.jsonb(message.headers),
                message.originMessageId,
                0,
            ).returning()
            .fetchOneInto(DeadLetterMessage::class.java)
            ?: throw IllegalStateException("Failed to save message to DLQ")
}
