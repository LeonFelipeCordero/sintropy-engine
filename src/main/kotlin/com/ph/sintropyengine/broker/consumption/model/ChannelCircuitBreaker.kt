package com.ph.sintropyengine.broker.consumption.model

import java.time.OffsetDateTime
import java.util.UUID

data class ChannelCircuitBreaker(
    val circuitBreakerId: Long,
    val circuitBreakerUuid: UUID,
    val channelId: Long,
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
