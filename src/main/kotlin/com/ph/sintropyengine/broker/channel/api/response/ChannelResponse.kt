package com.ph.sintropyengine.broker.channel.api.response

import com.ph.sintropyengine.broker.channel.model.Channel
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.model.RoutingKeyCircuitState
import com.ph.sintropyengine.broker.consumption.model.CircuitState

data class ChannelResponse(
    val name: String,
    val channelType: ChannelType,
    val routingKeys: List<String>,
    val consumptionType: ConsumptionType?,
    val routingKeysCircuitState: List<RoutingKeyCircuitStateResponse>,
)

data class RoutingKeyCircuitStateResponse(
    val routingKey: String,
    val circuitState: CircuitState,
)

fun Channel.toResponse(): ChannelResponse =
    ChannelResponse(
        name = name,
        channelType = channelType,
        routingKeys = routingKeys.toList(),
        consumptionType = consumptionType,
        routingKeysCircuitState = routingKeysCircuitState.map { it.toResponse() },
    )

fun RoutingKeyCircuitState.toResponse(): RoutingKeyCircuitStateResponse =
    RoutingKeyCircuitStateResponse(
        routingKey = routingKey,
        circuitState = circuitState,
    )
