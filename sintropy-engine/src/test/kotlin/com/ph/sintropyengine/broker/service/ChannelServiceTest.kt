package com.ph.sintropyengine.broker.service

import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.model.ChannelType.*
import com.ph.sintropyengine.broker.model.ConsumptionType.*
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.quarkus.test.junit.QuarkusTest

@QuarkusTest
class ChannelServiceTest : IntegrationTestBase() {

    @Inject
    private lateinit var channelService: ChannelService

    @BeforeEach
    fun setUp() {
        clean()
    }

    @Test
    fun `should create and persist a channel`() {
        val createdChannel = channelService.createChannel("test", QUEUE, listOf("test.1"), STANDARD)
        val fetchedChannel = channelService.findByName("test")

        assertThat(createdChannel).usingRecursiveComparison().isEqualTo(fetchedChannel);
    }

    @Test
    fun `should fail if the channel name already exist`() {
        channelService.createChannel("test", QUEUE, listOf("test.1"), STANDARD)
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { channelService.createChannel("test", QUEUE, listOf("test.1"), STANDARD) }
            .withMessage("Channel with name test already exists")
    }

    @Test
    fun `should fail if the channel routing keys are not provided`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { channelService.createChannel("test", QUEUE, listOf(), STANDARD) }
            .withMessage("At least one routing key must be provided")
    }

    @Test
    fun `should add a routing key to a channel`() {
        val createdChannel = channelService.createChannel("test", QUEUE, listOf("test.1"), STANDARD)
        channelService.addRoutingKey(createdChannel.channelId!!, "test.2")
    }

    @Test
    fun `should fail if the routing key already exists`() {
        val createdChannel = channelService.createChannel("test", QUEUE, listOf("test.1"), STANDARD)
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { channelService.addRoutingKey(createdChannel.channelId!!, "test.1") }
            .withMessage("RoutingKey test.1 already exists")
    }

    @Test
    fun `should delete a channel`() {
        val createdChannel = channelService.createChannel("test", QUEUE, listOf("test.1"), STANDARD)
        channelService.deleteChannel(createdChannel.channelId!!)
        val foundChannel = channelService.findById(createdChannel.channelId)
        assertThat(foundChannel).isNull()
    }

    @Test
    fun `should if channel already deleted`() {
        val createdChannel = channelService.createChannel("test", QUEUE, listOf("test.1"), STANDARD)
        channelService.deleteChannel(createdChannel.channelId!!)
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { channelService.deleteChannel(createdChannel.channelId) }
            .withMessageContainingAll("Channel with id", "not found")
    }

    @Test
    fun `should create a fifo channel normally`() {
        val createdChannel = channelService.createChannel("test", QUEUE, listOf("test.1"), FIFO)
        val foundChannel = channelService.findById(createdChannel.channelId!!)
        assertThat(foundChannel?.consumptionType).isEqualTo(FIFO)
    }

    @Test
    fun `should create an steam and have no queue details`() {
        val createdChannel = channelService.createChannel("test", STREAM, listOf("test.1"))
        val foundChannel = channelService.findById(createdChannel.channelId!!)
        assertThat(foundChannel?.channelType).isEqualTo(STREAM)
        assertThat(foundChannel?.consumptionType).isNull()
    }

}