package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.IntegrationTestBase
import io.quarkus.test.junit.QuarkusTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

@QuarkusTest
class MessageServiceTest : IntegrationTestBase() {

    @BeforeEach
    fun setUp() {
        clean()
    }

    @Test
    fun `should retrigger message and return message when it exists`() {
        val message = publishMessage()

        val retriggeredMessage = messageService.retriggerMessage(message.messageId)

        assertThat(retriggeredMessage).isNotNull
        assertThat(retriggeredMessage.messageId).isEqualTo(message.messageId)
        assertThat(retriggeredMessage.channelId).isEqualTo(message.channelId)
        assertThat(retriggeredMessage.producerId).isEqualTo(message.producerId)
        assertThat(retriggeredMessage.routingKey).isEqualTo(message.routingKey)
    }

    @Test
    fun `should fail to retrigger message when message does not exist`() {
        val nonExistentId = UUID.randomUUID()

        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { messageService.retriggerMessage(nonExistentId) }
            .withMessage("Message with id $nonExistentId was not found")
    }

    @Test
    fun `should stream messages from a given time without end time`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer)
        Thread.sleep(50)
        val message2 = publishMessage(channel, producer)
        Thread.sleep(50)
        val message3 = publishMessage(channel, producer)

        val from = message1.timestamp!!.minusSeconds(1)

        val messages = messageService.streamFromToByChannelIdAndRoutingKey(
            channelName = channel.name,
            routingKey = channel.routingKeys.first(),
            from = from,
            to = null
        )

        assertThat(messages).hasSize(3)
        assertThat(messages.map { it.messageId }).containsExactlyInAnyOrder(
            message1.messageId,
            message2.messageId,
            message3.messageId
        )
    }

    @Test
    fun `should stream messages within a time range`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer)
        Thread.sleep(50)
        val message2 = publishMessage(channel, producer)
        Thread.sleep(50)
        val message3 = publishMessage(channel, producer)

        val from = message1.timestamp!!.minusSeconds(1)
        val to = message2.timestamp!!.plusNanos(1)

        val messages = messageService.streamFromToByChannelIdAndRoutingKey(
            channelName = channel.name,
            routingKey = channel.routingKeys.first(),
            from = from,
            to = to
        )

        assertThat(messages).hasSize(2)
        assertThat(messages.map { it.messageId }).containsExactlyInAnyOrder(
            message1.messageId,
            message2.messageId
        )
    }

    @Test
    fun `should return empty list when no messages match the time range`() {
        val (channel, producer) = createChannelWithProducer()
        publishMessage(channel, producer)

        val futureTime = OffsetDateTime.now().plusHours(1)

        val messages = messageService.streamFromToByChannelIdAndRoutingKey(
            channelName = channel.name,
            routingKey = channel.routingKeys.first(),
            from = futureTime,
            to = null
        )

        assertThat(messages).isEmpty()
    }

    @Test
    fun `should return empty list when channel has no messages`() {
        val channel = createChannel()

        val messages = messageService.streamFromToByChannelIdAndRoutingKey(
            channelName = channel.name,
            routingKey = channel.routingKeys.first(),
            from = OffsetDateTime.now().minusHours(1),
            to = null
        )

        assertThat(messages).isEmpty()
    }

    @Test
    fun `should fail to stream when channel does not exist`() {
        val nonExistentChannel = "non-existent-channel"

        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                messageService.streamFromToByChannelIdAndRoutingKey(
                    channelName = nonExistentChannel,
                    routingKey = "some.key",
                    from = OffsetDateTime.now(),
                    to = null
                )
            }
            .withMessage("Channel with name $nonExistentChannel was not found")
    }

    @Test
    fun `should fail to stream when routing key does not exist for channel`() {
        val channel = createChannel()
        val invalidRoutingKey = "invalid.routing.key"

        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                messageService.streamFromToByChannelIdAndRoutingKey(
                    channelName = channel.name,
                    routingKey = invalidRoutingKey,
                    from = OffsetDateTime.now(),
                    to = null
                )
            }
            .withMessage("Routing key $invalidRoutingKey does not exist for channel ${channel.name}")
    }

    @Test
    fun `should only return messages for the specified routing key`() {
        val channel = createChannel()
        channelRepository.addRoutingKey(channel.channelId!!, "other.routing.key")
        val producer = createProducer(channel)

        val message1 = publishMessage(channel, producer, channel.routingKeys.first())
        val message2 = publishMessage(channel, producer, "other.routing.key")

        val messages = messageService.streamFromToByChannelIdAndRoutingKey(
            channelName = channel.name,
            routingKey = channel.routingKeys.first(),
            from = OffsetDateTime.now().minusHours(1),
            to = null
        )

        assertThat(messages).hasSize(1)
        assertThat(messages.first().messageId).isEqualTo(message1.messageId)
    }

    @Test
    fun `streamFromAll should return all messages for channel and routing key`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer)
        val message2 = publishMessage(channel, producer)
        val message3 = publishMessage(channel, producer)

        val messages = messageService.streamFromAll(
            channelName = channel.name,
            routingKey = channel.routingKeys.first()
        )

        assertThat(messages).hasSize(3)
        assertThat(messages.map { it.messageId }).containsExactlyInAnyOrder(
            message1.messageId,
            message2.messageId,
            message3.messageId
        )
    }

    @Test
    fun `streamFromAll should return empty list when channel has no messages`() {
        val channel = createChannel()

        val messages = messageService.streamFromAll(
            channelName = channel.name,
            routingKey = channel.routingKeys.first()
        )

        assertThat(messages).isEmpty()
    }

    @Test
    fun `streamFromAll should fail when channel does not exist`() {
        val nonExistentChannel = "non-existent-channel"

        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                messageService.streamFromAll(
                    channelName = nonExistentChannel,
                    routingKey = "some.key"
                )
            }
            .withMessage("Channel with name $nonExistentChannel was not found")
    }

    @Test
    fun `streamFromAll should fail when routing key does not exist for channel`() {
        val channel = createChannel()
        val invalidRoutingKey = "invalid.routing.key"

        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                messageService.streamFromAll(
                    channelName = channel.name,
                    routingKey = invalidRoutingKey
                )
            }
            .withMessage("Routing key $invalidRoutingKey does not exist for channel ${channel.name}")
    }

    @Test
    fun `streamFromAll should only return messages for the specified routing key`() {
        val channel = createChannel()
        channelRepository.addRoutingKey(channel.channelId!!, "other.routing.key")
        val producer = createProducer(channel)

        val message1 = publishMessage(channel, producer, channel.routingKeys.first())
        val message2 = publishMessage(channel, producer, "other.routing.key")

        val messages = messageService.streamFromAll(
            channelName = channel.name,
            routingKey = channel.routingKeys.first()
        )

        assertThat(messages).hasSize(1)
        assertThat(messages.first().messageId).isEqualTo(message1.messageId)
    }
}