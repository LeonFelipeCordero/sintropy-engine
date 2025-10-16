package com.ph.syntropyengine

import com.ph.syntropyengine.broker.model.Channel
import com.ph.syntropyengine.broker.model.ChannelType
import com.ph.syntropyengine.broker.model.ConnectionType
import com.ph.syntropyengine.broker.model.Consumer
import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.model.Producer
import java.time.OffsetDateTime
import java.util.UUID

object Fixtures {

    const val DEFAULT_ROUTING_KEY = "test.1"
    const val DEFAULT_PRODUCER_NAME = "test_producer"
    const val DEFAULT_CHANNEL_NAME = "test_channel"
    const val DEFAULT_MESSAGE = "test_message"

    fun createChannel(
        channelId: UUID? = null,
        channelType: ChannelType = ChannelType.STANDARD,
        routingKeys: MutableList<String> = mutableListOf(DEFAULT_ROUTING_KEY)
    ): Channel {
        return Channel(
            channelId = channelId,
            name = UUID.randomUUID().toString(),
            channelType = channelType,
            consumers = emptyList(),
            routingKeys = routingKeys
        )
    }

    fun createConsumer(
        channelId: UUID,
        routingKey: String = DEFAULT_ROUTING_KEY,
        consumerId: UUID? = null,
        connectionType: ConnectionType = ConnectionType.POLLING
    ): Consumer {
        return Consumer(
            consumerId = consumerId,
            channelId = channelId,
            routingKey = routingKey,
            connectionType = connectionType
        )
    }

    fun createProducer(
        channelId: UUID,
        producerId: UUID? = null
    ): Producer {
        return Producer(
            producerId = producerId,
            name = UUID.randomUUID().toString(),
            channelId = channelId,
        )
    }

    fun createMessage(
        channelId: UUID,
        producerId: UUID,
        routingKey: String = DEFAULT_ROUTING_KEY,
        timestamp: OffsetDateTime = OffsetDateTime.now().minusSeconds(1)
    ): Message {
        return Message(
            messageId = UUID.randomUUID(),
            timestamp = timestamp,
            channelId = channelId,
            producerId = producerId,
            routingKey = routingKey,
            message = DEFAULT_MESSAGE,
        )
    }
}
