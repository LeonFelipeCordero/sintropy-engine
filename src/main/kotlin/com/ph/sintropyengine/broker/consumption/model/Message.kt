package com.ph.sintropyengine.broker.consumption.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.ph.sintropyengine.broker.shared.utils.JsonbDeserializer
import com.ph.sintropyengine.broker.shared.utils.JsonbSerializer
import org.jooq.JSONB
import java.time.OffsetDateTime
import java.util.UUID

data class Message(
    val messageId: Long,
    val messageUuid: UUID,
    val timestamp: OffsetDateTime? = null,
    val channelId: Long,
    val producerId: Long,
    val routingKey: String,
    @param:JsonSerialize(using = JsonbSerializer::class)
    @param:JsonDeserialize(using = JsonbDeserializer::class)
    val message: JSONB,
    @param:JsonSerialize(using = JsonbSerializer::class)
    @param:JsonDeserialize(using = JsonbDeserializer::class)
    val headers: JSONB,
    val status: MessageStatus,
    val lastDelivered: OffsetDateTime? = null,
    val deliveredTimes: Int,
    val originMessageId: UUID? = null,
)

enum class MessageStatus {
    READY,
    IN_FLIGHT,
    FAILED,
}

data class MessagePreStore(
    val originMessageId: UUID?,
    val channelName: String,
    val producerName: String,
    val routingKey: String,
    val message: String,
    val headers: String,
)
