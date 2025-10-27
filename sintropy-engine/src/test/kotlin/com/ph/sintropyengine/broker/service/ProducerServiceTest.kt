package com.ph.sintropyengine.broker.service

import com.ph.sintropyengine.Fixtures
import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.model.MessageStatus
import io.quarkus.test.junit.QuarkusTest
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
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
        assertThat(producer).usingRecursiveComparison().isEqualTo(fetchedProducer)
    }

    @Test
    fun `should store a message`() {
        val (channel, producer) = createChannelWithProducer()

        val createdMessage = producerService.publishMessage(
            Fixtures.createMessage(channel.channelId!!, producer.producerId!!)
        )

        val all = messageRepository.findAll()
        assertThat(all).hasSize(1)
        assertThat(createdMessage).usingRecursiveComparison().isEqualTo(all.first())
    }

    @Test
    fun `should fail if message doesn't exist`() {
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                producerService.publishMessage(Fixtures.createMessage(UUID.randomUUID(), UUID.randomUUID()))
            }.withMessageContainingAll("Channel", "not found")
    }

    @Test
    fun `should fail if routing key is incorrect`() {
        val (channel, producer) = createChannelWithProducer()

        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                producerService.publishMessage(
                    Fixtures.createMessage(
                        channel.channelId!!,
                        producer.producerId!!,
                        "test.2"
                    )
                )
            }.withMessageContainingAll("Channel", "does not have routing-key test.2")
    }

    @Test
    fun `should create a message and it trigger and insert in event log`() {
        val (channel, producer) = createChannelWithProducer()

        producerService.publishMessage(
            Fixtures.createMessage(channel.channelId!!, producer.producerId!!)
        )

        val messages = messageRepository.findAll()
        val eventLogs = messageRepository.findAllEventLog()
        assertThat(messages).hasSize(1)
        assertThat(eventLogs).hasSize(1)
        val message = messages.first()
        val eventLog = eventLogs.first()
        assertThat(message.messageId).isEqualTo(eventLog.messageId)
        assertThat(message.timestamp).isEqualTo(eventLog.timestamp)
        assertThat(message.channelId).isEqualTo(eventLog.channelId)
        assertThat(message.producerId).isEqualTo(eventLog.producerId)
        assertThat(message.routingKey).isEqualTo(eventLog.routingKey)

        assertThat(message.status).isEqualTo(MessageStatus.READY)
        assertThat(message.lastDelivered).isNull()
        assertThat(message.deliveredTimes).isZero

        assertThat(eventLog.processed).isFalse
    }
}