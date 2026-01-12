package com.ph.sintropyengine

import com.ph.sintropyengine.broker.channel.model.Channel
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.model.MessagePreStore
import com.ph.sintropyengine.broker.consumption.model.MessageStatus
import com.ph.sintropyengine.broker.producer.api.PublishMessageRequest
import com.ph.sintropyengine.broker.producer.model.Producer
import org.jooq.JSONB
import java.util.UUID

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
        consumptionType: ConsumptionType? = if (channelType == QUEUE) STANDARD else null,
    ): Channel =
        Channel(
            channelId = channelId,
            name = UUID.randomUUID().toString(),
            channelType = channelType,
            routingKeys = routingKeys.toMutableList(),
            consumptionType = consumptionType,
        )

    fun createProducer(
        channelId: UUID,
        producerId: UUID? = null,
    ): Producer =
        Producer(
            producerId = producerId,
            name = UUID.randomUUID().toString(),
            channelId = channelId,
        )

    fun createMessage(
        channelId: UUID,
        producerId: UUID,
        routingKey: String = DEFAULT_ROUTING_KEY,
    ): Message =
        Message(
            messageId = UUID.randomUUID(),
            channelId = channelId,
            producerId = producerId,
            routingKey = routingKey,
            message = JSONB.jsonb(DEFAULT_MESSAGE),
            headers = JSONB.jsonb(DEFAULT_ATTRIBUTES),
            status = MessageStatus.READY,
            lastDelivered = null,
            deliveredTimes = 0,
        )

    fun createMessagePreStore(
        channelId: UUID,
        producerId: UUID,
        routingKey: String = DEFAULT_ROUTING_KEY,
    ): MessagePreStore =
        MessagePreStore(
            channelId = channelId,
            producerId = producerId,
            routingKey = routingKey,
            message = DEFAULT_MESSAGE,
            headers = DEFAULT_ATTRIBUTES,
        )

    fun createMessageRequest(
        channelName: String,
        producerName: String,
        routingKey: String = DEFAULT_ROUTING_KEY,
    ): PublishMessageRequest =
        PublishMessageRequest(
            channelName = channelName,
            producerName = producerName,
            routingKey = routingKey,
            message = DEFAULT_MESSAGE,
            headers = DEFAULT_ATTRIBUTES,
        )
}
