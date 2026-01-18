package com.ph.sintropyengine.broker.consumption.api.response

import com.ph.sintropyengine.broker.consumption.model.ChannelCircuitBreaker
import com.ph.sintropyengine.broker.consumption.model.CircuitState
import java.time.OffsetDateTime
import java.util.UUID

data class CircuitBreakerResponse(
    val channelName: String,
    val routingKey: String,
    val state: CircuitState,
    val openedAt: OffsetDateTime?,
    val failedMessageId: UUID?,
)

fun ChannelCircuitBreaker.toResponse(channelName: String): CircuitBreakerResponse =
    CircuitBreakerResponse(
        channelName = channelName,
        routingKey = routingKey,
        state = state,
        openedAt = openedAt,
        failedMessageId = failedMessageId,
    )
