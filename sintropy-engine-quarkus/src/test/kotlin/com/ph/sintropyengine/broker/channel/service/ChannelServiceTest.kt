package com.ph.sintropyengine.broker.channel.service

import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.service.ChannelService
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
        val createdChannel =
            channelService.createChannel(
                "test",
                ChannelType.QUEUE,
                listOf("test.1"),
                ConsumptionType.STANDARD,
            )
        val fetchedChannel = channelService.findByName("test")

        Assertions.assertThat(createdChannel).usingRecursiveComparison().isEqualTo(fetchedChannel)
    }

    @Test
    fun `should fail if the channel name already exist`() {
        channelService.createChannel("test", ChannelType.QUEUE, listOf("test.1"), ConsumptionType.STANDARD)
        Assertions
            .assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                channelService.createChannel(
                    "test",
                    ChannelType.QUEUE,
                    listOf("test.1"),
                    ConsumptionType.STANDARD,
                )
            }.withMessage("Channel with name test already exists")
    }

    @Test
    fun `should fail if the channel routing keys are not provided`() {
        Assertions
            .assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { channelService.createChannel("test", ChannelType.QUEUE, listOf(), ConsumptionType.STANDARD) }
            .withMessage("At least one routing key must be provided")
    }

    @Test
    fun `should add a routing key to a channel`() {
        val createdChannel =
            channelService.createChannel(
                "test",
                ChannelType.QUEUE,
                listOf("test.1"),
                ConsumptionType.STANDARD,
            )
        channelService.addRoutingKey(createdChannel.channelId!!, "test.2")
    }

    @Test
    fun `should fail if the routing key already exists`() {
        val createdChannel =
            channelService.createChannel(
                "test",
                ChannelType.QUEUE,
                listOf("test.1"),
                ConsumptionType.STANDARD,
            )
        Assertions
            .assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { channelService.addRoutingKey(createdChannel.channelId!!, "test.1") }
            .withMessage("RoutingKey test.1 already exists")
    }

    @Test
    fun `should delete a channel`() {
        val createdChannel =
            channelService.createChannel(
                "test",
                ChannelType.QUEUE,
                listOf("test.1"),
                ConsumptionType.STANDARD,
            )
        channelService.deleteChannel(createdChannel.channelId!!)
        val foundChannel = channelService.findById(createdChannel.channelId)
        Assertions.assertThat(foundChannel).isNull()
    }

    @Test
    fun `should if channel already deleted`() {
        val createdChannel =
            channelService.createChannel(
                "test",
                ChannelType.QUEUE,
                listOf("test.1"),
                ConsumptionType.STANDARD,
            )
        channelService.deleteChannel(createdChannel.channelId!!)
        Assertions
            .assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { channelService.deleteChannel(createdChannel.channelId) }
            .withMessageContainingAll("Channel with id", "not found")
    }

    @Test
    fun `should create a fifo channel normally`() {
        val createdChannel =
            channelService.createChannel(
                "test",
                ChannelType.QUEUE,
                listOf("test.1"),
                ConsumptionType.FIFO,
            )
        val foundChannel = channelService.findById(createdChannel.channelId!!)
        Assertions.assertThat(foundChannel?.consumptionType).isEqualTo(ConsumptionType.FIFO)
    }

    @Test
    fun `should create an steam and have no queue details`() {
        val createdChannel = channelService.createChannel("test", ChannelType.STREAM, listOf("test.1"))
        val foundChannel = channelService.findById(createdChannel.channelId!!)
        Assertions.assertThat(foundChannel?.channelType).isEqualTo(ChannelType.STREAM)
        Assertions.assertThat(foundChannel?.consumptionType).isNull()
    }
}
