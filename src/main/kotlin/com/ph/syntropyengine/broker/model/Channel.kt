package com.ph.syntropyengine.broker.model

import java.util.UUID

data class Channel(
    val channelId: UUID? = null,
    val name: String,
    val consumers: List<Consumer>,
    val routingKeys: MutableList<String>,
) {

    constructor(channelId: UUID, name: String, routingKeys: MutableList<String>) :
            this(channelId, name, emptyList(), routingKeys)

    fun containsRoutingKey(routingKey: String): Boolean = routingKeys.contains(routingKey)
}
