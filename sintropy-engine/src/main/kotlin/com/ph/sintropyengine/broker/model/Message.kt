package com.ph.sintropyengine.broker.model

import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.JSONB

data class Message(
    val messageId: UUID,
    val timestamp: OffsetDateTime,
    val channelId: UUID,
    val producerId: UUID,
    val routingKey: String,
    val message: JSONB,
    val headers: JSONB,
    val status: MessageStatus = MessageStatus.READY,
    val lastDelivered: OffsetDateTime? = null,
    val deliveredTimes: Int = 0
)

enum class MessageStatus {
    READY,
    IN_FLIGHT,
    FAILED
}