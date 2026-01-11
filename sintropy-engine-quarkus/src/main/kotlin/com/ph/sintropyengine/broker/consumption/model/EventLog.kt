package com.ph.sintropyengine.broker.consumption.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.ph.sintropyengine.broker.shared.utils.JsonbDeserializer
import com.ph.sintropyengine.broker.shared.utils.JsonbSerializer
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.JSONB

data class EventLog(
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
    val processed: Boolean
)