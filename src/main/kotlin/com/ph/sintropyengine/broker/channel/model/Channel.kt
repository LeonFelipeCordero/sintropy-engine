package com.ph.sintropyengine.broker.channel.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.ph.sintropyengine.broker.consumption.model.CircuitState
import java.util.UUID

// TODO create different data classes for queue and stream that extend from Channel which will be an interface
data class Channel(
    val channelId: Long? = null,
    val channelUuid: UUID? = null,
    val name: String,
    val channelType: ChannelType,
    val routingKeys: MutableList<String>,
    val consumptionType: ConsumptionType? = null,
    val routingKeysCircuitState: List<RoutingKeyCircuitState> = listOf(),
) {
    fun containsRoutingKey(routingKey: String): Boolean = routingKeys.contains(routingKey)

    @JsonIgnore
    fun getConsumptionOrFail(): ConsumptionType {
        if (channelType != ChannelType.QUEUE) {
            throw IllegalStateException("ChannelType is not QUEUE, there force it can't have consumption type")
        }

        if (consumptionType == null) {
            throw IllegalStateException("ChannelType is QUEUE, but consumption type is null")
        }

        return consumptionType
    }

    fun canWriteMessage(routingKey: String): Boolean = !isFifo() || !(isFifo() && isCircuitOpen(routingKey))

    fun isFifo(): Boolean = channelType == ChannelType.STREAM || consumptionType == ConsumptionType.FIFO

    fun isCircuitOpen(routingKey: String): Boolean =
        routingKeysCircuitState
            .find { it.routingKey == routingKey }
            ?.circuitState == CircuitState.OPEN
}

enum class ChannelType {
    QUEUE,
    STREAM,
}

enum class ConsumptionType {
    STANDARD,
    FIFO,
}

data class RoutingKeyCircuitState(
    val routingKey: String,
    val circuitState: CircuitState,
)
