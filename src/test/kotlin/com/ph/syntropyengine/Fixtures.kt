package com.ph.syntropyengine

import com.ph.syntropyengine.broker.model.Channel
import com.ph.syntropyengine.broker.model.ChannelType
import com.ph.syntropyengine.broker.model.ConnectionType
import com.ph.syntropyengine.broker.model.Consumer
import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.model.Producer
import com.ph.syntropyengine.utils.Patterns.routing
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

    private data class TestData(
        private val channelProducer1: ChannelProducer,
        private val channelProducer2: ChannelProducer,
        private val channelProducer3: ChannelProducer,
    ) {
        fun channel1() = channelProducer1.channel
        fun channel2() = channelProducer2.channel
        fun channel3() = channelProducer3.channel
        fun producer1() = channelProducer1.producer
        fun producer2() = channelProducer2.producer
        fun producer3() = channelProducer3.producer
        fun routing10() = routing(channelProducer1.channel.channelId!!, channelProducer1.channel.routingKeys[0])
        fun routing11() = routing(channelProducer1.channel.channelId!!, channelProducer1.channel.routingKeys[1])
        fun routing12() = routing(channelProducer1.channel.channelId!!, channelProducer1.channel.routingKeys[2])
        fun routing20() = routing(channelProducer2.channel.channelId!!, channelProducer2.channel.routingKeys[0])
        fun routing21() = routing(channelProducer2.channel.channelId!!, channelProducer2.channel.routingKeys[1])
        fun routing30() = routing(channelProducer3.channel.channelId!!, channelProducer3.channel.routingKeys[0])

        fun toMap(): MutableMap<String, MutableList<Message>> {
            return mutableMapOf(
                routing10() to mutableListOf(),
                routing11() to mutableListOf(),
                routing12() to mutableListOf(),
                routing20() to mutableListOf(),
                routing21() to mutableListOf(),
                routing30() to mutableListOf(),
            )
        }
    }
}
