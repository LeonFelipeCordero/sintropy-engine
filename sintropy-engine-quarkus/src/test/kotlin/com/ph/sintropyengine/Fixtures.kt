package com.ph.sintropyengine

import com.ph.sintropyengine.broker.chennel.model.Channel
import com.ph.sintropyengine.broker.chennel.model.ChannelType
import com.ph.sintropyengine.broker.chennel.model.ChannelType.*
import com.ph.sintropyengine.broker.chennel.model.ConsumptionType
import com.ph.sintropyengine.broker.chennel.model.ConsumptionType.*
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.model.MessagePreStore
import com.ph.sintropyengine.broker.consumption.model.MessageStatus
import com.ph.sintropyengine.broker.producer.model.Producer
import com.ph.sintropyengine.broker.producer.api.PublishMessageRequest
import java.util.UUID
import org.jooq.JSONB

object Fixtures {

    const val DEFAULT_ROUTING_KEY = "test.1"
    const val DEFAULT_PRODUCER_NAME = "test_producer"
    const val DEFAULT_CHANNEL_NAME = "test_channel"
    const val DEFAULT_MESSAGE = """{"id": "test1345", "number": 10}"""
    const val DEFAULT_ATTRIBUTES = """{"header1": "12345", "header2": "abcdefg"}"""

    fun createChannel(
        channelId: UUID? = null,
        channelType: ChannelType = QUEUE,
        routingKeys: List<String> = listOf(DEFAULT_ROUTING_KEY),
        consumptionType: ConsumptionType = STANDARD,
    ): Channel {
        return Channel(
            channelId = channelId,
            name = UUID.randomUUID().toString(),
            channelType = channelType,
            routingKeys = routingKeys.toMutableList(),
            consumptionType = consumptionType,
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
    ): Message {
        return Message(
            messageId = UUID.randomUUID(),
            channelId = channelId,
            producerId = producerId,
            routingKey = routingKey,
            message = JSONB.jsonb(DEFAULT_MESSAGE),
            headers = JSONB.jsonb(DEFAULT_ATTRIBUTES),
            status = MessageStatus.READY,
            lastDelivered = null,
            deliveredTimes = 0
        )
    }

    fun createMessagePreStore(
        channelId: UUID,
        producerId: UUID,
        routingKey: String = DEFAULT_ROUTING_KEY,
    ): MessagePreStore {
        return MessagePreStore(
            channelId = channelId,
            producerId = producerId,
            routingKey = routingKey,
            message = DEFAULT_MESSAGE,
            headers = DEFAULT_ATTRIBUTES,
        )
    }

    fun createMessageRequest(
        channelName: String,
        producerName: String,
        routingKey: String = DEFAULT_ROUTING_KEY,
    ): PublishMessageRequest {
        return PublishMessageRequest(
            channelName = channelName,
            producerName = producerName,
            routingKey = routingKey,
            message = DEFAULT_MESSAGE,
            headers = DEFAULT_ATTRIBUTES
        )
    }
}
