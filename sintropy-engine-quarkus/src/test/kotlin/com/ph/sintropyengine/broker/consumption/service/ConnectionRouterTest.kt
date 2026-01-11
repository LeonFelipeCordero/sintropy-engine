package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.Fixtures
import com.ph.sintropyengine.Fixtures.DEFAULT_ROUTING_KEY
import com.ph.sintropyengine.broker.chennel.service.ChannelService
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConnectionRouterTest {

    private val channelService = mockk<ChannelService>()

    private lateinit var connectionRouter: ConnectionRouter

    val defaultChannel = Fixtures.createChannel(UUID.randomUUID(), routingKeys = listOf(DEFAULT_ROUTING_KEY, "test.2"),)

    @BeforeEach
    fun setUp() {
        every { channelService.findByName(any()) } returns defaultChannel

        connectionRouter = ConnectionRouter(channelService)
    }

    @Test
    fun `should add new connection with routing`() = runTest {
        val connectionId = UUID.randomUUID().toString()
        connectionRouter.add(connectionId, defaultChannel.name, DEFAULT_ROUTING_KEY)

        val connections = connectionRouter.getByRoutingKey(routing(defaultChannel.channelId!!, DEFAULT_ROUTING_KEY))

        assertThat(connections).hasSize(1)
        assertThat(connections.first()).isEqualTo(connectionId)
    }

    @Test
    fun `should add new connection with new routing key to existing channel`() = runTest {
        val connectionId1 = UUID.randomUUID().toString()
        val connectionId2 = UUID.randomUUID().toString()
        connectionRouter.add(connectionId1, defaultChannel.name, DEFAULT_ROUTING_KEY)
        connectionRouter.add(connectionId2, defaultChannel.name, "test.2")

        val consumersByRoutingKey1 =
            connectionRouter.getByRoutingKey(routing(defaultChannel.channelId!!, DEFAULT_ROUTING_KEY))
        val consumersByRoutingKey2 = connectionRouter.getByRoutingKey(routing(defaultChannel.channelId, "test.2"))

        assertThat(consumersByRoutingKey1).hasSize(1)
        assertThat(consumersByRoutingKey2).hasSize(1)
        assertThat(consumersByRoutingKey1.first()).isEqualTo(connectionId1)
        assertThat(consumersByRoutingKey2.first()).isEqualTo(connectionId2)
    }

    @Test
    fun `should add new connection to existing routing key to existing channel`() = runTest {
        val connectionId1 = UUID.randomUUID().toString()
        val connectionId2 = UUID.randomUUID().toString()
        connectionRouter.add(connectionId1, defaultChannel.name, DEFAULT_ROUTING_KEY)
        connectionRouter.add(connectionId2, defaultChannel.name, DEFAULT_ROUTING_KEY)

        val connections = connectionRouter.getByRoutingKey(routing(defaultChannel.channelId!!, DEFAULT_ROUTING_KEY))

        assertThat(connections).hasSize(2)
        assertThat(connections.first()).isEqualTo(connectionId1)
        assertThat(connections[1]).isEqualTo(connectionId2)
    }

    @Test
    fun `should not add same connection twice`() = runTest {
        val connectionId = UUID.randomUUID().toString()
        connectionRouter.add(connectionId, defaultChannel.name, DEFAULT_ROUTING_KEY)

        val connections = connectionRouter.getByRoutingKey(routing(defaultChannel.channelId!!, DEFAULT_ROUTING_KEY))

        assertThat(connections).hasSize(1)
        assertThat(connections.first()).isEqualTo(connectionId)
    }

    @Test
    fun `should remove consumer from routing table`() = runTest {
        val connectionId = UUID.randomUUID().toString()
        connectionRouter.add(connectionId, defaultChannel.name, DEFAULT_ROUTING_KEY)

        connectionRouter.remove(connectionId)

        val connections = connectionRouter.getByRoutingKey(routing(defaultChannel.channelId!!, DEFAULT_ROUTING_KEY))

        assertThat(connections).hasSize(0)
    }

    @Test
    fun `should remove consumer twice from routing table and not fal`() = runTest {
        val connectionId = UUID.randomUUID().toString()
        connectionRouter.add(connectionId, defaultChannel.name, DEFAULT_ROUTING_KEY)

        connectionRouter.remove(connectionId)
        connectionRouter.remove(connectionId)

        val connections = connectionRouter.getByRoutingKey(routing(defaultChannel.channelId!!, DEFAULT_ROUTING_KEY))

        assertThat(connections).hasSize(0)
    }

    @Test
    fun `should spread keys when multiple coroutines write at the same time`() = runTest {
        clearMocks(channelService)

        val channel1 = Fixtures.createChannel(
            channelId = UUID.randomUUID(),
            routingKeys = mutableListOf("test.1.1", "test.1.2", "test.1.3"),
        )
        every { channelService.findByName(channel1.name) } returns channel1

        val channel2 = Fixtures.createChannel(
            channelId = UUID.randomUUID(),
            routingKeys = mutableListOf("test.2.1", "test.2.2"),
        )
        every { channelService.findByName(channel2.name) } returns channel2

        val channel3 = Fixtures.createChannel(channelId = UUID.randomUUID(), routingKeys = mutableListOf("test.3.1"),)
        every { channelService.findByName(channel3.name) } returns channel3

        val mutex = Mutex()
        val connections = mutableMapOf<String, MutableList<String>>()

        val launchLastLevel: suspend () -> Unit = {
            val pair = when (Random.nextInt(1, 4)) {
                1 -> Triple(channel1.channelId!!, channel1.name, channel1.routingKeys[Random.nextInt(0, 3)])

                2 -> Triple(channel2.channelId!!, channel2.name, channel2.routingKeys[Random.nextInt(0, 2)])

                3 -> Triple(channel3.channelId!!, channel3.name, channel3.routingKeys.first())

                else -> {
                    throw IllegalStateException("Failure setting test data")
                }
            }
            val connectionId = UUID.randomUUID().toString()
            val key = routing(pair.first, pair.third)
            mutex.withLock {
                if (connections.contains(key)) {
                    connections[key]!!.add(connectionId)
                } else {
                    connections[key] = mutableListOf(connectionId)
                }
            }
            connectionRouter.add(connectionId, pair.second, pair.third)
        }

        val launchFirstLevel: suspend () -> Unit = {
            repeat(10) {
                launch {
                    repeat(10) {
                        launch {
                            launchLastLevel()
                        }
                    }
                }
            }
        }

        launchFirstLevel()

        delay(1000)

        connections.forEach { (key, value) ->
            val fetchedConnections = connectionRouter.getByRoutingKey(key)
            assertThat(fetchedConnections).isEqualTo(value)
        }
    }
}