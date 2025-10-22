package com.ph.syntropyengine.utils

import com.ph.syntropyengine.broker.model.Consumer
import com.ph.syntropyengine.broker.model.EventLog
import com.ph.syntropyengine.broker.model.Message
import java.util.UUID

object Patterns {

    fun routing(channelId: UUID, routingKey: String) = "${channelId}|${routingKey}"

    fun Consumer.routing() = routing(this.channelId, this.routingKey)

    fun Message.routing() = routing(this.channelId, this.routingKey)

    fun EventLog.routing() = routing(this.channelId, this.routingKey)
}