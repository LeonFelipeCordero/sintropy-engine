package com.ph.sintropyengine.broker.consumption.repository

import com.ph.sintropyengine.broker.consumption.model.DeadLetterMessage
import com.ph.sintropyengine.jooq.generated.Tables.DEAD_LETTER_QUEUE
import jakarta.enterprise.context.ApplicationScoped
import org.jooq.DSLContext
import java.util.UUID

@ApplicationScoped
class DeadLetterQueueRepository(
    private val context: DSLContext,
) {
    fun findById(dlqEntryId: UUID): DeadLetterMessage? =
        context
            .selectFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.DLQ_ENTRY_ID.eq(dlqEntryId))
            .fetchOneInto(DeadLetterMessage::class.java)

    fun findByMessageId(messageId: UUID): DeadLetterMessage? =
        context
            .selectFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.MESSAGE_ID.eq(messageId))
            .orderBy(DEAD_LETTER_QUEUE.FAILED_AT.desc())
            .fetchOneInto(DeadLetterMessage::class.java)

    fun findByChannelIdAndRoutingKey(
        channelId: UUID,
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
        channelId: UUID,
        routingKey: String,
    ): List<DeadLetterMessage> =
        context
            .selectFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.CHANNEL_ID.eq(channelId))
            .and(DEAD_LETTER_QUEUE.ROUTING_KEY.eq(routingKey))
            .fetchInto(DeadLetterMessage::class.java)

    fun delete(dlqEntryId: UUID) {
        context
            .deleteFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.DLQ_ENTRY_ID.eq(dlqEntryId))
            .execute()
    }

    fun deleteByIds(dlqEntryIds: List<UUID>) {
        context
            .deleteFrom(DEAD_LETTER_QUEUE)
            .where(DEAD_LETTER_QUEUE.DLQ_ENTRY_ID.`in`(dlqEntryIds))
            .execute()
    }

    fun deleteByChannelIdAndRoutingKey(
        channelId: UUID,
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
}
