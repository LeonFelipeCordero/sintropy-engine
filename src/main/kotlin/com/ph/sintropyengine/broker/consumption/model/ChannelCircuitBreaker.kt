package com.ph.sintropyengine.broker.consumption.model

import java.time.OffsetDateTime
import java.util.UUID

data class ChannelCircuitBreaker(
    val circuitId: UUID,
    val channelId: UUID,
    val routingKey: String,
    val state: CircuitState,
    val openedAt: OffsetDateTime? = null,
    val failedMessageId: UUID? = null,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null,
)

enum class CircuitState {
    CLOSED,
    OPEN,
}
