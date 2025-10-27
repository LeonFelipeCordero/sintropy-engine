package com.ph.syntropyengine.broker.model

import java.util.UUID

data class Channel(
    val channelId: UUID? = null,
    val name: String,
    val channelType: ChannelType,
    val consumers: List<Consumer>,
    val routingKeys: MutableList<String>,
) {

    constructor(channelId: UUID, name: String, routingKeys: MutableList<String>, channelType: ChannelType) :
            this(
                channelId = channelId,
                name = name,
                channelType = channelType,
                consumers = emptyList(),
                routingKeys = routingKeys
            )

    fun containsRoutingKey(routingKey: String): Boolean = routingKeys.contains(routingKey)
}

enum class ChannelType {
    STANDARD,
    FIFO
}