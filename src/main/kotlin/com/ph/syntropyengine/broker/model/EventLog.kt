package com.ph.syntropyengine.broker.model

import java.time.OffsetDateTime
import java.util.UUID

data class EventLog(
    val messageId: UUID,
    val timestamp: OffsetDateTime,
    val channelId: UUID,
    val producerId: UUID,
    val routingKey: String,
    val message: String,
    val headers: String,
    val processed: Boolean
)