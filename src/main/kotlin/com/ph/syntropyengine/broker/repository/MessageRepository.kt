package com.ph.syntropyengine.broker.repository

import com.ph.syntropyengine.broker.model.EventLog
import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.jooq.generated.Tables.EVENT_LOG
import com.ph.syntropyengine.jooq.generated.Tables.MESSAGES
import com.ph.syntropyengine.jooq.generated.enums.MessageStatusType
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL.select
import org.springframework.stereotype.Repository

@Repository
class MessageRepository(
    private val context: DSLContext
) {
    fun save(message: Message): Message =
        context.insertInto(
            MESSAGES,
            MESSAGES.MESSAGE_ID,
            MESSAGES.TIMESTAMP,
            MESSAGES.CHANNEL_ID,
            MESSAGES.PRODUCER_ID,
            MESSAGES.ROUTING_KEY,
            MESSAGES.MESSAGE,
        ).values(
            message.messageId,
            message.timestamp,
            message.channelId,
            message.producerId,
            message.routingKey,
            message.message
        ).returning().fetchOneInto(Message::class.java)
            ?: throw IllegalStateException("Something went wrong persisting the message")

    /**
     * Poll messages that are ready and lock on the record for update.
     * Could be it's needed to use an advisory lock such as pg_try_advisory_xact_lock for better consistency
     */
    fun pollFromQueueAndRoutingKey(channelId: UUID, routingKey: String, pollingCount: Int): List<Message> {
        val subQuery = select(MESSAGES.MESSAGE_ID)
            .from(MESSAGES)
            .where(MESSAGES.CHANNEL_ID.eq(channelId))
            .and(MESSAGES.ROUTING_KEY.eq(routingKey))
            .and(
                MESSAGES.STATUS.eq(MessageStatusType.READY)
                    .or(
                        MESSAGES.STATUS.ge(MessageStatusType.IN_FLIGHT)
                            .and(
                                MESSAGES.LAST_DELIVERED.lessThan(OffsetDateTime.now().minusMinutes(15))
                                    .and(MESSAGES.DELIVERED_TIMES.lessThan(4))
                            )
                    )
            ).orderBy(MESSAGES.TIMESTAMP)
            .limit(pollingCount)
            .forUpdate().skipLocked() // TODO would it be needed here
        return context.update(MESSAGES)
            .set(MESSAGES.STATUS, MessageStatusType.IN_FLIGHT)
            .set(MESSAGES.LAST_DELIVERED, OffsetDateTime.now())
            .set(MESSAGES.DELIVERED_TIMES, MESSAGES.DELIVERED_TIMES.add(1))
            .set(MESSAGES.UPDATED_AT, OffsetDateTime.now())
            .where(MESSAGES.MESSAGE_ID.`in`(subQuery))
            .returning()
            .fetchInto(Message::class.java)
    }

    fun markAsFailed(messageId: UUID) {
        context.update(MESSAGES)
            .set(MESSAGES.STATUS, MessageStatusType.FAILED)
            .set(MESSAGES.UPDATED_AT, OffsetDateTime.now())
            .where(MESSAGES.MESSAGE_ID.eq(messageId))
            .execute()
    }

    fun dequeue(messageId: UUID) {
        context.delete(MESSAGES)
            .where(MESSAGES.MESSAGE_ID.eq(messageId))
            .execute()
    }

    fun findById(messageId: UUID): Message? =
        context.selectFrom(MESSAGES)
            .where(MESSAGES.MESSAGE_ID.eq(messageId))
            .fetchOneInto(Message::class.java)

    fun findAll(): List<Message> = context.selectFrom(MESSAGES).fetchInto(Message::class.java)
    fun findAllEventLog(): List<EventLog> = context.selectFrom(EVENT_LOG).fetchInto(EventLog::class.java)


    /**
     * Testing only
     */
    fun deleteAll() {
        context.delete(MESSAGES).execute()
        context.delete(EVENT_LOG).execute()
    }

    /**
     * Testing only
     */
    fun setMessageDeliveriesOutOfScope(messageId: UUID) {
        context.update(MESSAGES)
            .set(MESSAGES.STATUS, MessageStatusType.IN_FLIGHT)
            .set(MESSAGES.LAST_DELIVERED, OffsetDateTime.now().minusMinutes(16))
            .set(MESSAGES.DELIVERED_TIMES, 4)
            .set(MESSAGES.UPDATED_AT, OffsetDateTime.now())
            .where(MESSAGES.MESSAGE_ID.eq(messageId))
            .execute()
    }
}