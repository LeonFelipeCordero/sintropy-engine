package com.ph.sintropyengine.broker.channel.service

import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.consumption.model.CircuitState
import io.quarkus.test.junit.QuarkusTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class ChannelServiceTest : IntegrationTestBase() {
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

        assertThat(createdChannel)
            .usingRecursiveComparison()
            .ignoringFields("routingKeysCircuitState")
            .isEqualTo(fetchedChannel)
    }

    @Test
    fun `should fail if the channel name already exist`() {
        channelService.createChannel("test", ChannelType.QUEUE, listOf("test.1"), ConsumptionType.STANDARD)
        assertThatExceptionOfType(IllegalStateException::class.java)
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
        assertThatExceptionOfType(IllegalArgumentException::class.java)
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
        channelService.addRoutingKeyByName(createdChannel.name, "test.2")
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
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { channelService.addRoutingKeyByName(createdChannel.name, "test.1") }
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
        assertThat(foundChannel).isNull()
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
        assertThatExceptionOfType(IllegalStateException::class.java)
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
        assertThat(foundChannel?.consumptionType).isEqualTo(ConsumptionType.FIFO)
    }

    @Test
    fun `should create an steam and have no queue details`() {
        val createdChannel = channelService.createChannel("test", ChannelType.STREAM, listOf("test.1"))
        val foundChannel = channelService.findById(createdChannel.channelId!!)
        assertThat(foundChannel?.channelType).isEqualTo(ChannelType.STREAM)
        assertThat(foundChannel?.consumptionType).isNull()
    }

    @Test
    fun `should link circuit state per routing key to channel when fetched by ID`() {
        val channel = channelService.createChannel("test", ChannelType.STREAM, listOf("test.1", "test.2"))

        val foundChannelById = channelService.findById(channel.channelId!!)!!

        assertThat(foundChannelById.routingKeysCircuitState.first().routingKey).isEqualTo("test.1")
        assertThat(foundChannelById.routingKeysCircuitState.first().circuitState).isEqualTo(CircuitState.CLOSED)
        assertThat(foundChannelById.routingKeysCircuitState[1].routingKey).isEqualTo("test.2")
        assertThat(foundChannelById.routingKeysCircuitState[1].circuitState).isEqualTo(CircuitState.CLOSED)
    }

    @Test
    fun `should link circuit state per routing key to channel when fetched by name`() {
        val channel = channelService.createChannel("test", ChannelType.STREAM, listOf("test.1", "test.2"))

        val foundChannelByName = channelService.findByName(channel.name)!!

        assertThat(foundChannelByName.routingKeysCircuitState.first().routingKey).isEqualTo("test.1")
        assertThat(foundChannelByName.routingKeysCircuitState.first().circuitState).isEqualTo(CircuitState.CLOSED)
        assertThat(foundChannelByName.routingKeysCircuitState[1].routingKey).isEqualTo("test.2")
        assertThat(foundChannelByName.routingKeysCircuitState[1].circuitState).isEqualTo(CircuitState.CLOSED)
    }
}
