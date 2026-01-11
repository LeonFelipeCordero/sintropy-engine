package com.ph.sintropyengine.broker.shared.utils

import com.ph.sintropyengine.broker.chennel.model.Channel
import com.ph.sintropyengine.broker.consumption.model.EventLog
import com.ph.sintropyengine.broker.consumption.model.Message
import java.util.UUID

object Patterns {

    fun routing(channelId: UUID, routingKey: String) = "${channelId}|${routingKey}"

    fun Message.routing() = routing(this.channelId, this.routingKey)

    fun EventLog.routing() = routing(this.channelId, this.routingKey)

    fun Channel.routing(routingKey: String) = routing(this.channelId!!, routingKey)
}