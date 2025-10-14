package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.Fixtures
import com.ph.syntropyengine.broker.model.Consumer
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

class MessageRouterTest {

    private lateinit var router: MessageRouter

    @BeforeEach
    fun setUp() {
        router = MessageRouter()
    }

    @Test
    fun `should add new channel with new routing key with new consumer`() = runTest {
        val channelId = UUID.randomUUID()
        val consumer = Fixtures.createConsumer(channelId = channelId, consumerId = UUID.randomUUID())

        router.addConsumer(consumer)

        val consumers = router.getConsumersByRouting(channelId, consumer.routingKey)

        assertThat(consumers).hasSize(1)
        assertThat(consumers.first()).usingRecursiveComparison().isEqualTo(consumer)
    }

    @Test
    fun `should add new routing key with new consumer to existing channel`() = runTest {
        val channelId = UUID.randomUUID()
        val consumer = Fixtures.createConsumer(channelId = channelId, consumerId = UUID.randomUUID())
        val consumer2 = Fixtures.createConsumer(channelId = channelId, "test.2", consumerId = UUID.randomUUID())

        router.addConsumer(consumer)
        router.addConsumer(consumer2)

        val consumersByRoutingKey1 = router.getConsumersByRouting(channelId, consumer.routingKey)
        val consumersByRoutingKey2 = router.getConsumersByRouting(channelId, consumer2.routingKey)

        assertThat(consumersByRoutingKey1).hasSize(1)
        assertThat(consumersByRoutingKey2).hasSize(1)
        assertThat(consumersByRoutingKey1.first()).usingRecursiveComparison().isEqualTo(consumer)
        assertThat(consumersByRoutingKey2.first()).usingRecursiveComparison().isEqualTo(consumer2)
    }

    @Test
    fun `should add new consumer to existing routing key to existing channel`() = runTest {
        val channelId = UUID.randomUUID()
        val consumer = Fixtures.createConsumer(channelId = channelId, consumerId = UUID.randomUUID())
        val consumer2 = Fixtures.createConsumer(channelId = channelId, consumerId = UUID.randomUUID())

        router.addConsumer(consumer)
        router.addConsumer(consumer2)

        val consumers = router.getConsumersByRouting(channelId, consumer.routingKey)

        assertThat(consumers).hasSize(2)
        assertThat(consumers.first()).usingRecursiveComparison().isEqualTo(consumer)
        assertThat(consumers[1]).usingRecursiveComparison().isEqualTo(consumer2)
    }

    @Test
    fun `should not add same consumer twice`() = runTest {
        val channelId = UUID.randomUUID()
        val consumer = Fixtures.createConsumer(channelId = channelId, consumerId = UUID.randomUUID())

        router.addConsumer(consumer)
        router.addConsumer(consumer)

        val consumers = router.getConsumersByRouting(channelId, consumer.routingKey)

        assertThat(consumers).hasSize(1)
        assertThat(consumers.first()).usingRecursiveComparison().isEqualTo(consumer)
    }

    @Test
    fun `should remove consumer from routing table`() = runTest {
        val channelId = UUID.randomUUID()
        val consumer = Fixtures.createConsumer(channelId = channelId, consumerId = UUID.randomUUID())

        router.addConsumer(consumer)
        router.removeConsumer(consumer)

        val consumers = router.getConsumersByRouting(channelId, consumer.routingKey)

        assertThat(consumers).hasSize(0)
    }

    @Test
    fun `should remove consumer twice from routing table and not fal`() = runTest {
        val channelId = UUID.randomUUID()
        val consumer = Fixtures.createConsumer(channelId = channelId, consumerId = UUID.randomUUID())

        router.addConsumer(consumer)
        router.removeConsumer(consumer)
        router.removeConsumer(consumer)

        val consumers = router.getConsumersByRouting(channelId, consumer.routingKey)

        assertThat(consumers).hasSize(0)
    }

    @Test
    fun `should spread keys when multiple coroutines write at the same time`() = runTest {
        val channel1 = Fixtures.createChannel(
            UUID.randomUUID(),
            mutableListOf("test.1.1", "test.1.2", "test.1.3")
        )

        val channel2 = Fixtures.createChannel(UUID.randomUUID(), mutableListOf("test.2.1", "test.2.2"))

        val channel3 = Fixtures.createChannel(UUID.randomUUID(), mutableListOf("test.3.1"))

        val mutex = Mutex()
        val consumers = mutableListOf<Consumer>()

        val launchLastLevel: suspend () -> Unit = {
            val consumer = when (Random.nextInt(1, 4)) {
                1 -> Fixtures.createConsumer(
                    channelId = channel1.channelId!!,
                    routingKey = channel1.routingKeys[Random.nextInt(0, 3)],
                    consumerId = UUID.randomUUID()
                )

                2 -> Fixtures.createConsumer(
                    channelId = channel2.channelId!!,
                    routingKey = channel2.routingKeys[Random.nextInt(0, 2)],
                    consumerId = UUID.randomUUID()
                )


                3 -> Fixtures.createConsumer(
                    channelId = channel3.channelId!!,
                    routingKey = channel3.routingKeys.first(),
                    consumerId = UUID.randomUUID()
                )

                else -> {
                    throw IllegalStateException("Failure setting test data")
                }
            }
            mutex.withLock {
                consumers.add(consumer)
            }
            router.addConsumer(consumer)
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

        val consumersChannel1 = consumers.filter { it.channelId == channel1.channelId }.groupBy { it.routingKey }
        val consumersChannel2 = consumers.filter { it.channelId == channel2.channelId }.groupBy { it.routingKey }
        val consumersChannel3 = consumers.filter { it.channelId == channel3.channelId }.groupBy { it.routingKey }

        for (entry in consumersChannel1.entries) {
            val storedConsumer = router.getConsumersByRouting(channel1.channelId!!, entry.key)
            assertThat(entry.value).usingRecursiveComparison().isEqualTo(storedConsumer)
        }
        for (entry in consumersChannel2.entries) {
            val storedConsumer = router.getConsumersByRouting(channel2.channelId!!, entry.key)
            assertThat(entry.value).usingRecursiveComparison().isEqualTo(storedConsumer)
        }
        for (entry in consumersChannel3.entries) {
            val storedConsumer = router.getConsumersByRouting(channel3.channelId!!, entry.key)
            assertThat(entry.value).usingRecursiveComparison().isEqualTo(storedConsumer)
        }
    }

}