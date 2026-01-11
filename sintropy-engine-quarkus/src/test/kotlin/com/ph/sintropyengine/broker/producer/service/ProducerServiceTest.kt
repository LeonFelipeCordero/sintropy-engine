package com.ph.sintropyengine.broker.producer.service

import com.ph.sintropyengine.Fixtures
import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.consumption.model.MessageStatus
import io.quarkus.test.junit.QuarkusTest
import java.util.UUID
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
        Assertions.assertThat(producer).usingRecursiveComparison().isEqualTo(fetchedProducer)
    }

    @Test
    fun `should store a message`() {
        val (channel, producer) = createChannelWithProducer()

        val createdMessage = producerService.publishMessage(
            Fixtures.createMessageRequest(channel.name, producer.name)
        )

        val all = messageRepository.findAll()
        Assertions.assertThat(all).hasSize(1)
        Assertions.assertThat(createdMessage).usingRecursiveComparison().isEqualTo(all.first())
    }

    @Test
    fun `should fail if message doesn't exist`() {
        Assertions.assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                producerService.publishMessage(Fixtures.createMessageRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
            }.withMessageContainingAll("Channel", "not found")
    }

    @Test
    fun `should fail if routing key is incorrect`() {
        val (channel, producer) = createChannelWithProducer()

        Assertions.assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                producerService.publishMessage(
                    Fixtures.createMessageRequest(
                        channel.name,
                        producer.name,
                        "test.2"
                    )
                )
            }.withMessageContainingAll("Channel", "does not have routing-key test.2")
    }

    @Test
    fun `should create a message and it trigger and insert in event log`() {
        val (channel, producer) = createChannelWithProducer()

        producerService.publishMessage(
            Fixtures.createMessageRequest(channel.name, producer.name)
        )

        val messages = messageRepository.findAll()
        val eventLogs = messageRepository.findAllEventLog()
        Assertions.assertThat(messages).hasSize(1)
        Assertions.assertThat(eventLogs).hasSize(1)
        val message = messages.first()
        val eventLog = eventLogs.first()
        Assertions.assertThat(message.messageId).isEqualTo(eventLog.messageId)
        Assertions.assertThat(message.timestamp).isEqualTo(eventLog.timestamp)
        Assertions.assertThat(message.channelId).isEqualTo(eventLog.channelId)
        Assertions.assertThat(message.producerId).isEqualTo(eventLog.producerId)
        Assertions.assertThat(message.routingKey).isEqualTo(eventLog.routingKey)

        Assertions.assertThat(message.status).isEqualTo(MessageStatus.READY)
        Assertions.assertThat(message.lastDelivered).isNull()
        Assertions.assertThat(message.deliveredTimes).isZero

        Assertions.assertThat(eventLog.processed).isFalse
    }
}