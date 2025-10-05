package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.IntegrationTestBase
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.springframework.beans.factory.annotation.Autowired

class ChannelServiceTest : IntegrationTestBase() {

    @Autowired
    private lateinit var channelService: ChannelService

    @BeforeTest
    fun setUp() {
        clean()
    }

    @Test
    fun `should create and persist a channel`() {
        val createdChannel = channelService.createChannel("test", listOf("test.1"))
        val fetchedChannel = channelService.findByIdName("test")

        assertThat(createdChannel).usingRecursiveComparison().isEqualTo(fetchedChannel);
    }

    @Test
    fun `should fail if the channel name already exist`() {
        channelService.createChannel("test", listOf("test.1"))
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { channelService.createChannel("test", listOf("test.1")) }
            .withMessage("Channel with name test already exists")
    }

    @Test
    fun `should fail if the channel routing keys are not provided`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { channelService.createChannel("test", listOf()) }
            .withMessage("At least one routing key must be provided")
    }

    @Test
    fun `should add a routing key to a channel`() {
        val createdChannel = channelService.createChannel("test", listOf("test.1"))
        channelService.addRoutingKey(createdChannel.channelId!!, "test.2")
    }

    @Test
    fun `should fail if the routing key already exists`() {
        val createdChannel = channelService.createChannel("test", listOf("test.1"))
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { channelService.addRoutingKey(createdChannel.channelId!!, "test.1") }
            .withMessage("RoutingKey test.1 already exists")
    }

    @Test
    fun `should delete a channel`() {
        val createdChannel = channelService.createChannel("test", listOf("test.1"))
        channelService.deleteChannel(createdChannel.channelId!!)
        val foundChannel = channelService.findById(createdChannel.channelId)
        assertThat(foundChannel).isNull()
    }

    @Test
    fun `should if channel already deleted`() {
        val createdChannel = channelService.createChannel("test", listOf("test.1"))
        channelService.deleteChannel(createdChannel.channelId!!)
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { channelService.deleteChannel(createdChannel.channelId) }
            .withMessageContainingAll("Channel with id", "not found")
    }
}