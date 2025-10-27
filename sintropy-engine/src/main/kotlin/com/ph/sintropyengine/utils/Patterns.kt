package com.ph.sintropyengine.utils

import com.ph.sintropyengine.broker.model.EventLog
import com.ph.sintropyengine.broker.model.Message
import com.ph.sintropyengine.broker.model.Consumer
import java.util.UUID

object Patterns {

    fun routing(channelId: UUID, routingKey: String) = "${channelId}|${routingKey}"

    fun Consumer.routing() = routing(this.channelId, this.routingKey)

    fun Message.routing() = routing(this.channelId, this.routingKey)

    fun EventLog.routing() = routing(this.channelId, this.routingKey)
}