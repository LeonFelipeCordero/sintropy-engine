package com.ph.sintropyengine.broker.model

import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.JSONB

data class EventLog(
    val messageId: UUID,
    val timestamp: OffsetDateTime,
    val channelId: UUID,
    val producerId: UUID,
    val routingKey: String,
    val message: JSONB,
    val headers: JSONB,
    val processed: Boolean
)