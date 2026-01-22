package com.ph.sintropyengine.broker.consumption.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.ph.sintropyengine.broker.shared.utils.JsonbDeserializer
import com.ph.sintropyengine.broker.shared.utils.JsonbSerializer
import org.jooq.JSONB
import java.time.OffsetDateTime
import java.util.UUID

data class MessageLog(
    val messageId: Long? = null,
    val messageUuid: UUID,
    val timestamp: OffsetDateTime,
    val channelId: Long,
    val producerId: Long,
    val routingKey: String,
    @param:JsonSerialize(using = JsonbSerializer::class)
    @param:JsonDeserialize(using = JsonbDeserializer::class)
    val message: JSONB,
    @param:JsonSerialize(using = JsonbSerializer::class)
    @param:JsonDeserialize(using = JsonbDeserializer::class)
    val headers: JSONB,
    val processed: Boolean,
    val originMessageId: UUID? = null,
)
