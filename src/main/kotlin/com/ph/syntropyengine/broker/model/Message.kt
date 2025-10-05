package com.ph.syntropyengine.broker.model

import java.time.OffsetDateTime
import java.util.UUID

data class Message(
    val messageId: UUID,
    val timestamp: OffsetDateTime,
    val channelId: UUID,
    val producerId: UUID,
    val routingKey: String,
    val message: String,
)