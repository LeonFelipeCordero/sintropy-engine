package com.ph.syntropyengine

import com.ph.syntropyengine.broker.model.Channel
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

    fun createChannel(): Channel {
        return Channel(
            channelId = null,
            name = DEFAULT_CHANNEL_NAME,
            consumers = emptyList(),
            routingKeys = mutableListOf(DEFAULT_ROUTING_KEY)
        )
    }

    fun createConsumer(channelId: UUID): Consumer {
        return Consumer(
            channelId = channelId,
            routingKey = DEFAULT_ROUTING_KEY,
        )
    }

    fun createProducer(channelId: UUID): Producer {
        return Producer(
            name = DEFAULT_PRODUCER_NAME,
            channelId = channelId,
        )
    }

    fun createMessage(channelId: UUID, producerId: UUID, routingKye: String = DEFAULT_ROUTING_KEY): Message {
        return Message(
            messageId = UUID.randomUUID(),
            timestamp = OffsetDateTime.now().minusMinutes(1),
            channelId = channelId,
            producerId = producerId,
            routingKey = routingKye,
            message = DEFAULT_MESSAGE,
        )
    }
}
