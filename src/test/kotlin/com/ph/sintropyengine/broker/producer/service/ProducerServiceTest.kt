package com.ph.sintropyengine.broker.producer.service

import com.ph.sintropyengine.Fixtures
import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.consumption.model.MessageStatus
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType

@QuarkusTest
class ProducerServiceTest : IntegrationTestBase() {
    @BeforeEach
    fun setUp() {
        clean()
    }

    @Test
    fun `should create producer`() {
        val channel = createChannel()
        val producer = producerService.createProducer(channel.name, channel.name)
        val fetchedProducer = producerService.findById(producer.producerId!!)
        assertThat(producer).usingRecursiveComparison().isEqualTo(fetchedProducer)
    }

    @Test
    fun `should store a message`() {
        val (channel, producer) = createChannelWithProducer()

        val createdMessage =
            producerService.publishMessage(
                Fixtures.createMessagePreStore(channel.name, producer.name),
            )

        val all = messageRepository.findAll()
        assertThat(all).hasSize(1)
        assertThat(createdMessage).usingRecursiveComparison().isEqualTo(all.first())
    }

    @Test
    fun `should fail if message doesn't exist`() {
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                producerService.publishMessage(Fixtures.createMessagePreStore(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
            }.withMessageContainingAll("Channel", "not found")
    }

    @Test
    fun `should fail if routing key is incorrect`() {
        val (channel, producer) = createChannelWithProducer()

        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                producerService.publishMessage(
                    Fixtures.createMessagePreStore(
                        channel.name,
                        producer.name,
                        "test.2",
                    ),
                )
            }.withMessageContaining("Channel with name ${channel.name} and routing key test.2 not found")
    }

    @Test
    fun `should fail if producer is not linked to channel`() {
        val (channel1, producer1) = createChannelWithProducer()
        val channel2 = createChannel()

        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                producerService.publishMessage(
                    Fixtures.createMessagePreStore(
                        channelName = channel2.name,
                        producerName = producer1.name,
                        routingKey = channel2.routingKeys.first(),
                    ),
                )
            }.withMessageContaining("is not linked to channel")
    }

    @Test
    fun `should create a message and it trigger and insert in message log`() {
        val (channel, producer) = createChannelWithProducer()

        producerService.publishMessage(
            Fixtures.createMessagePreStore(channel.name, producer.name),
        )

        val messages = messageRepository.findAll()
        val messageLogs = messageRepository.findAllMessageLog()
        assertThat(messages).hasSize(1)
        assertThat(messageLogs).hasSize(1)
        val message = messages.first()
        val messageLog = messageLogs.first()
        assertThat(message.messageId).isEqualTo(messageLog.messageId)
        assertThat(message.messageUuid).isEqualTo(messageLog.messageUuid)
        assertThat(message.timestamp).isEqualTo(messageLog.timestamp)
        assertThat(message.channelId).isEqualTo(messageLog.channelId)
        assertThat(message.producerId).isEqualTo(messageLog.producerId)
        assertThat(message.routingKey).isEqualTo(messageLog.routingKey)

        assertThat(message.status).isEqualTo(MessageStatus.READY)
        assertThat(message.lastDelivered).isNull()
        assertThat(message.deliveredTimes).isZero

        assertThat(messageLog.processed).isFalse
    }

    @Test
    fun `should fail if producer name contains white spaces`() {
        val channel = createChannel()

        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy {
                producerService.createProducer("producer name", channel.name)
            }.withMessage("Producer name must not contain white spaces")
    }
}
