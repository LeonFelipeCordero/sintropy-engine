package com.ph.sintropyengine.broker.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.ph.sintropyengine.utils.JsonbDeserializer
import com.ph.sintropyengine.utils.JsonbSerializer
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.JSONB

data class Message(
    val messageId: UUID,
    val timestamp: OffsetDateTime,
    val channelId: UUID,
    val producerId: UUID,
    val routingKey: String,
    @param:JsonSerialize(using = JsonbSerializer::class)
    @param:JsonDeserialize(using = JsonbDeserializer::class)
    val message: JSONB,
    @param:JsonSerialize(using = JsonbSerializer::class)
    @param:JsonDeserialize(using = JsonbDeserializer::class)
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