package com.ph.sintropyengine.broker.chennel.model

import java.util.UUID

// TODO create different data classes for queue and stream that extend from Channel which will be an interface
data class Channel(
    val channelId: UUID? = null,
    val name: String,
    val channelType: ChannelType,
    val routingKeys: MutableList<String>,
    val consumptionType: ConsumptionType? = null,
) {

    fun containsRoutingKey(routingKey: String): Boolean = routingKeys.contains(routingKey)

    fun getConsumptionOrFail(): ConsumptionType {
        if (channelType != ChannelType.QUEUE) {
            throw IllegalStateException("ChannelType is not QUEUE, there force it can't have consumption type")
        }

        if (consumptionType == null) {
            throw IllegalStateException("ChannelType is QUEUE, but consumption type is null")
        }

        return consumptionType
    }
}

data class Queue(
    val channelId: UUID,
    val consumptionType: ConsumptionType
)

enum class ChannelType {
    QUEUE,
    STREAM
}

enum class ConsumptionType {
    STANDARD,
    FIFO
}
