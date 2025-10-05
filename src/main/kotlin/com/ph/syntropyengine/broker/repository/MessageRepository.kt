package com.ph.syntropyengine.broker.repository

import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.jooq.generated.Tables.MESSAGES
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class MessageRepository(
    private val context: DSLContext
) {
    fun save(message: Message): Message {
        return context.insertInto(
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
    }

    fun findById(id: UUID): Message? {
        return context.selectFrom(MESSAGES)
            .where(MESSAGES.MESSAGE_ID.eq(id))
            .fetchOneInto(Message::class.java)
    }

    fun findAll(): List<Message> {
        return context.selectFrom(MESSAGES).fetchInto(Message::class.java)
    }

    fun deleteAll() {
        context.delete(MESSAGES).execute()
    }
}