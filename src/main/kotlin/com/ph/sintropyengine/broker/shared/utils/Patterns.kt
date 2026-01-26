package com.ph.sintropyengine.broker.shared.utils

import com.ph.sintropyengine.broker.channel.model.Channel
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.model.MessageLog
import com.ph.sintropyengine.broker.consumption.model.MessagePreStore
import java.util.UUID

object Patterns {
    fun routing(
        channelId: UUID,
        routingKey: String,
    ) = "$channelId|$routingKey"

    fun routing(
        channelName: String,
        routingKey: String,
    ) = "$channelName|$routingKey"

    fun Message.routing() = routing(this.channelId, this.routingKey)

    fun MessageLog.routing() = routing(this.channelId, this.routingKey)

    fun MessagePreStore.routing() = routing(this.channelName, this.routingKey)

    fun Channel.routing(routingKey: String) = routing(this.channelId!!, routingKey)
}
