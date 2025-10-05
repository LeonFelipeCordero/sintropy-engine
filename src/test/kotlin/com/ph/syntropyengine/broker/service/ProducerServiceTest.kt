package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.Fixtures
import com.ph.syntropyengine.IntegrationTestBase
import com.ph.syntropyengine.broker.model.Channel
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ProducerServiceTest : IntegrationTestBase() {

    @Autowired
    private lateinit var producerService: ProducerService

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
}