package com.ph.sintropyengine.broker.producer.service

import com.ph.sintropyengine.Fixtures
import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.consumption.model.MessageStatus
import io.quarkus.test.junit.QuarkusTest
import jdk.internal.net.http.common.Log.channel
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class ProducerServiceTest : IntegrationTestBase() {
    @BeforeEach
    fun setUp() {
        clean()
    }

    @Test
    fun `should create producer`() {
        val channel = createChannel()
        val producer = producerService.createProducer(channel.name)
        val fetchedProducer = producerService.findById(producer.producerId!!)
        Assertions.assertThat(producer).usingRecursiveComparison().isEqualTo(fetchedProducer)
    }

    @Test
    fun `should store a message`() {
        val (channel, producer) = createChannelWithProducer()

        val createdMessage =
            producerService.publishMessage(
                Fixtures.createMessagePreStore(channel.name, producer.name),
            )

        val all = messageRepository.findAll()
        Assertions.assertThat(all).hasSize(1)
        Assertions.assertThat(createdMessage).usingRecursiveComparison().isEqualTo(all.first())
    }

    @Test
    fun `should fail if message doesn't exist`() {
        Assertions
            .assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                producerService.publishMessage(Fixtures.createMessagePreStore(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
            }.withMessageContainingAll("Channel", "not found")
    }

    @Test
    fun `should fail if routing key is incorrect`() {
        val (channel, producer) = createChannelWithProducer()

        Assertions
            .assertThatExceptionOfType(IllegalStateException::class.java)
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
    fun `should create a message and it trigger and insert in message log`() {
        val (channel, producer) = createChannelWithProducer()

        producerService.publishMessage(
            Fixtures.createMessagePreStore(channel.name, producer.name),
        )

        val messages = messageRepository.findAll()
        val messageLogs = messageRepository.findAllMessageLog()
        Assertions.assertThat(messages).hasSize(1)
        Assertions.assertThat(messageLogs).hasSize(1)
        val message = messages.first()
        val messageLog = messageLogs.first()
        Assertions.assertThat(message.messageId).isEqualTo(messageLog.messageId)
        Assertions.assertThat(message.timestamp).isEqualTo(messageLog.timestamp)
        Assertions.assertThat(message.channelId).isEqualTo(messageLog.channelId)
        Assertions.assertThat(message.producerId).isEqualTo(messageLog.producerId)
        Assertions.assertThat(message.routingKey).isEqualTo(messageLog.routingKey)

        Assertions.assertThat(message.status).isEqualTo(MessageStatus.READY)
        Assertions.assertThat(message.lastDelivered).isNull()
        Assertions.assertThat(message.deliveredTimes).isZero

        Assertions.assertThat(messageLog.processed).isFalse
    }

    @Test
    fun `should fail if producer name contains white spaces`() {
        val channel = createChannel()

        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy {
                producerService.createProducer("producer name")
            }.withMessage("Producer name must not contain white spaces")
    }
}
