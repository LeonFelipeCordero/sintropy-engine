package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.Fixtures
import com.ph.syntropyengine.IntegrationTestBase
import com.ph.syntropyengine.broker.model.ConnectionType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ConsumerServiceTest : IntegrationTestBase() {

    @Autowired
    private lateinit var consumerService: ConsumerService

    @BeforeEach
    fun setUp() {
        clean()
    }

    @Test
    fun `should create consumer`() = runTest {
        val channel = createChannel()
        val consumer = consumerService.createConsumer(channel.name, Fixtures.DEFAULT_ROUTING_KEY, ConnectionType.POLLING)
        val fetchedConsumer = consumerService.findById(consumer.consumerId!!)
        assertThat(consumer).usingRecursiveComparison().isEqualTo(fetchedConsumer)
    }

    @Test
    fun `should fail if routing key does not exists`() = runTest {
        val channel = createChannel()
        assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
            runBlocking { consumerService.createConsumer(channel.name, "TEST.2", ConnectionType.POLLING) }
        }.withMessageContainingAll("Routing key", "not found in channel")
    }

    @Test
    fun `should delete a consumer`() {
        val (_, consumer) = createChannelAndConsumer()
        consumerService.deleteConsumer(consumer.consumerId!!)
        val foundConsumer = consumerService.findById(consumer.consumerId)
        assertThat(foundConsumer).isNull()
    }

    @Test
    fun `should delete a consumer and remove from the channel`() {
        val (channel, consumer) = createChannelAndConsumer()
        consumerService.deleteConsumer(consumer.consumerId!!)
        val foundByChannel = consumerService.findByChannel(channel.name)
        assertThat(foundByChannel).isEmpty()
    }

    @Test
    fun `should failed if consumer is deleted`() {
        val (_, consumer) = createChannelAndConsumer()
        consumerService.deleteConsumer(consumer.consumerId!!)
        assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
            consumerService.deleteConsumer(consumer.consumerId)
        }.withMessageContainingAll("Consumer", "not found")
    }

    @Test
    fun `should find consumers by channel`() {
        val (channel, consumer) = createChannelAndConsumer()
        val foundByChannel = consumerService.findByChannel(channel.name)
        assertThat(foundByChannel).hasSize(1)
        assertThat(foundByChannel.first()).usingRecursiveComparison().isEqualTo(consumer)
    }
}