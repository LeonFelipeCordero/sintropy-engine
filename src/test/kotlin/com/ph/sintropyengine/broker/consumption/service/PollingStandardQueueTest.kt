package com.ph.sintropyengine.broker.consumption.service

import com.arjuna.ats.internal.jdbc.drivers.modifiers.list
import com.ph.sintropyengine.Fixtures
import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class PollingStandardQueueTest : IntegrationTestBase() {
    @Inject
    private lateinit var pollingQueue: PollingStandardQueue

    @BeforeEach
    fun setUp() {
        clean()
    }

    @Test
    fun `should queue a message and poll`() {
        val message = publishMessage()

        val polledMessage = pollingQueue.poll(message.channelId, message.routingKey)

        assertThat(polledMessage).hasSize(1)
        assertThat(message)
            .usingRecursiveComparison()
            .ignoringFields("status", "lastDelivered", "deliveredTimes")
            .isEqualTo(polledMessage.first())
    }

    @Test
    fun `should not return anything if the message is consumed`() =
        runTest {
            val message = publishMessage()

            pollingQueue.poll(message.channelId, message.routingKey)
            val polledMessage = pollingQueue.poll(message.channelId, message.routingKey)

            assertThat(polledMessage).isEmpty()
        }

    @Test
    fun `should queue two messages and poll one by one`() =
        runTest {
            val (channel, producer) = createChannelWithProducer()
            val message1 = publishMessage(channel, producer)
            val message2 = publishMessage(channel, producer)

            val polledMessage1 = pollingQueue.poll(message1.channelId, message1.routingKey)
            val polledMessage2 = pollingQueue.poll(message1.channelId, message1.routingKey)

            assertThat(polledMessage1).hasSize(1)
            assertThat(polledMessage2).hasSize(1)
            assertThat(message1)
                .usingRecursiveComparison()
                .ignoringFields("status", "lastDelivered", "deliveredTimes")
                .isEqualTo(polledMessage1.first())
            assertThat(message2)
                .usingRecursiveComparison()
                .ignoringFields("status", "lastDelivered", "deliveredTimes")
                .isEqualTo(polledMessage2.first())
        }

    @Test
    fun `should queue a message and try to poll five`() =
        runTest {
            val message = publishMessage()

            val polledMessage = pollingQueue.poll(message.channelId, message.routingKey, pollingCount = 5)

            assertThat(polledMessage).hasSize(1)
            assertThat(message)
                .usingRecursiveComparison()
                .ignoringFields("status", "lastDelivered", "deliveredTimes")
                .isEqualTo(polledMessage.first())
        }

    @Test
    fun `should poll from an empty queue and do not fail`() {
        val polledMessage = pollingQueue.poll(UUID.randomUUID(), Fixtures.DEFAULT_ROUTING_KEY)

        assertThat(polledMessage).isEmpty()
    }

    @Test
    fun `should poll messages in chronological order one by one`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer)
        Thread.sleep(100)
        val message2 = publishMessage(channel, producer)
        Thread.sleep(200)
        val message3 = publishMessage(channel, producer)

        val polledMessage1 = pollingQueue.poll(channel.channelId!!, channel.routingKeys.first())
        val polledMessage2 = pollingQueue.poll(channel.channelId, channel.routingKeys.first())
        val polledMessage3 = pollingQueue.poll(channel.channelId, channel.routingKeys.first())

        assertThat(polledMessage1).hasSize(1)
        assertThat(polledMessage2).hasSize(1)
        assertThat(polledMessage3).hasSize(1)

        assertThat(polledMessage1.first())
            .usingRecursiveComparison()
            .ignoringFields("status", "lastDelivered", "deliveredTimes")
            .isEqualTo(message1)
        assertThat(polledMessage2.first())
            .usingRecursiveComparison()
            .ignoringFields("status", "lastDelivered", "deliveredTimes")
            .isEqualTo(message2)
        assertThat(polledMessage3.first())
            .usingRecursiveComparison()
            .ignoringFields("status", "lastDelivered", "deliveredTimes")
            .isEqualTo(message3)
    }

    @Test
    fun `should poll messages in chronological order all at once`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer)
        Thread.sleep(100)
        val message2 = publishMessage(channel, producer)
        Thread.sleep(200)
        val message3 = publishMessage(channel, producer)

        val polledMessages = pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 3)

        assertThat(polledMessages).hasSize(3)
        assertThat(polledMessages.map { it.messageId }).isEqualTo(
            listOf(
                message1.messageId,
                message2.messageId,
                message3.messageId,
            ),
        )
    }

    @Test
    fun `should not poll messages from other routing key`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 =
            publishMessage(
                channel,
                producer,
                channel.routingKeys.first(),
            )

        val secondRoutingKey = "test.2"
        channelService.addRoutingKeyByName(channel.name, secondRoutingKey)
        val message2 =
            publishMessage(
                channel,
                producer,
                secondRoutingKey,
            )

        val polledMessage1 = pollingQueue.poll(message1.channelId, message1.routingKey, pollingCount = 2)
        val polledMessage2 = pollingQueue.poll(message1.channelId, secondRoutingKey, pollingCount = 2)

        assertThat(polledMessage1).hasSize(1)
        assertThat(polledMessage2).hasSize(1)
        assertThat(polledMessage1.first())
            .usingRecursiveComparison()
            .ignoringFields("status", "lastDelivered", "deliveredTimes")
            .isEqualTo(message1)
        assertThat(polledMessage2.first())
            .usingRecursiveComparison()
            .ignoringFields("status", "lastDelivered", "deliveredTimes")
            .isEqualTo(message2)
    }

    @Test
    fun `should not poll messages from other channel`() {
        val message1 = publishMessage()
        val message2 = publishMessage()

        val polledMessage1 = pollingQueue.poll(message1.channelId, message1.routingKey, pollingCount = 2)
        val polledMessage2 = pollingQueue.poll(message2.channelId, message2.routingKey, pollingCount = 2)

        assertThat(polledMessage1).hasSize(1)
        assertThat(polledMessage2).hasSize(1)
        assertThat(polledMessage1.first())
            .usingRecursiveComparison()
            .ignoringFields("status", "lastDelivered", "deliveredTimes")
            .isEqualTo(message1)
        assertThat(polledMessage2.first())
            .usingRecursiveComparison()
            .ignoringFields("status", "lastDelivered", "deliveredTimes")
            .isEqualTo(message2)
    }

    @Test
    fun `should not pull a message that has been poll more than three times`() {
        val message = publishMessage()

        messageRepository.setMessageDeliveriesOutOfScope(message.messageId)

        val polledMessages = pollingQueue.poll(message.channelId, message.routingKey, pollingCount = 1)

        assertThat(polledMessages).hasSize(0)
    }

    @Test
    fun `should not pull a message that has been marked as failed`() {
        val message = publishMessage()

        pollingQueue.markAsFailed(message.messageId)

        val polledMessages = pollingQueue.poll(message.channelId, message.routingKey, pollingCount = 1)

        assertThat(polledMessages).hasSize(0)
    }

    @Test
    fun `should dequeue a message that is processed and mark it in the message log`() {
        val message = publishMessage()

        pollingQueue.poll(message.channelId, message.routingKey)

        pollingQueue.dequeue(message.messageId)

        val messages = messageRepository.findAll()
        val messageLogs = messageRepository.findAllMessageLog()

        assertThat(messages).hasSize(0)
        assertThat(messageLogs).hasSize(1)
        assertThat(messageLogs.first().processed).isTrue
    }

    @Test
    fun `should not dequeue a message that is on ready`() {
        val message = publishMessage()

        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { pollingQueue.dequeue(message.messageId) }
            .withMessageContainingAll("Message with id", "is still in status READY")
    }

    @Test
    fun `should fail to dequeue if message is not found`() {
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { pollingQueue.dequeue(UUID.randomUUID()) }
            .withMessageContainingAll("Message with id", "not found")
    }

    @Test
    fun `concurrent processing of messages`() =
        runTest {
            val testData = createTestData(ConsumptionType.STANDARD)

            val mutexConsumer = Mutex()
            val polledMessages = testData.toMap()

            val messagesCount = 1000
            val allWork =
                launch {
                    val producersJobs =
                        List(messagesCount) {
                            launch { launchProducers(testData) }
                        }

                    producersJobs.joinAll()

                    val consumersJobs = mutableListOf<Job>()
                    val pollingChannel = Channel<Message>(capacity = messagesCount)
                    repeat(10) {
                        consumersJobs.addAll(
                            listOf(
                                launch {
                                    launchConsumers(
                                        testData.channel1().channelId!!,
                                        testData.channel1().routingKeys[0],
                                        pollingChannel,
                                        pollingQueue,
                                    )
                                },
                                launch {
                                    launchConsumers(
                                        testData.channel1().channelId!!,
                                        testData.channel1().routingKeys[1],
                                        pollingChannel,
                                        pollingQueue,
                                    )
                                },
                                launch {
                                    launchConsumers(
                                        testData.channel1().channelId!!,
                                        testData.channel1().routingKeys[2],
                                        pollingChannel,
                                        pollingQueue,
                                    )
                                },
                                launch {
                                    launchConsumers(
                                        testData.channel2().channelId!!,
                                        testData.channel2().routingKeys[0],
                                        pollingChannel,
                                        pollingQueue,
                                    )
                                },
                                launch {
                                    launchConsumers(
                                        testData.channel2().channelId!!,
                                        testData.channel2().routingKeys[1],
                                        pollingChannel,
                                        pollingQueue,
                                    )
                                },
                                launch {
                                    launchConsumers(
                                        testData.channel3().channelId!!,
                                        testData.channel3().routingKeys[0],
                                        pollingChannel,
                                        pollingQueue,
                                    )
                                },
                            ),
                        )
                    }

                    consumersJobs.joinAll()
                    repeat(messagesCount) {
                        val message = pollingChannel.receive()
                        mutexConsumer.withLock {
                            polledMessages[message.routing()]!!.add(message)
                        }
                    }
                }

            allWork.join()

            val allMessageLog = messageRepository.findAllMessageLog().filter { it.processed }
            assertThat(allMessageLog).hasSize(messagesCount)
            assertThat(polledMessages.flatMap { (_, value) -> value }).hasSize(messagesCount)

            polledMessages.forEach { (key, messages) ->
                assertThat(messages)
                    .usingRecursiveComparison()
                    .ignoringFields("status", "lastDelivered", "deliveredTimes")
                    .isEqualTo(allMessageLog.filter { it.routing() == key }.sortedBy { it.timestamp })
            }
        }

    @Test
    fun `single processing of routing pair is always in chronological order`() =
        runTest {
            val testData = createTestData(ConsumptionType.STANDARD)

            val mutexConsumer = Mutex()
            val polledMessages = testData.toMap()

            val messagesCount = 1000
            val allWork =
                launch {
                    val producersJobs =
                        List(messagesCount) {
                            launch { launchProducers(testData) }
                        }

                    producersJobs.joinAll()

                    delay(1000)

                    val pollingChannel = Channel<Message>(capacity = messagesCount)
                    val consumersJobs =
                        listOf(
                            launch {
                                launchConsumers(
                                    testData.channel1().channelId!!,
                                    testData.channel1().routingKeys[0],
                                    pollingChannel,
                                    pollingQueue,
                                )
                            },
                            launch {
                                launchConsumers(
                                    testData.channel1().channelId!!,
                                    testData.channel1().routingKeys[1],
                                    pollingChannel,
                                    pollingQueue,
                                )
                            },
                            launch {
                                launchConsumers(
                                    testData.channel1().channelId!!,
                                    testData.channel1().routingKeys[2],
                                    pollingChannel,
                                    pollingQueue,
                                )
                            },
                            launch {
                                launchConsumers(
                                    testData.channel2().channelId!!,
                                    testData.channel2().routingKeys[0],
                                    pollingChannel,
                                    pollingQueue,
                                )
                            },
                            launch {
                                launchConsumers(
                                    testData.channel2().channelId!!,
                                    testData.channel2().routingKeys[1],
                                    pollingChannel,
                                    pollingQueue,
                                )
                            },
                            launch {
                                launchConsumers(
                                    testData.channel3().channelId!!,
                                    testData.channel3().routingKeys[0],
                                    pollingChannel,
                                    pollingQueue,
                                )
                            },
                        )

                    consumersJobs.joinAll()
                    repeat(messagesCount) {
                        val message = pollingChannel.receive()
                        mutexConsumer.withLock {
                            polledMessages[message.routing()]!!.add(message)
                        }
                    }
                }

            allWork.join()

            val allMessageLog = messageRepository.findAllMessageLog().filter { it.processed }
            assertThat(allMessageLog).hasSize(messagesCount)
            assertThat(polledMessages.flatMap { (_, value) -> value }).hasSize(messagesCount)

            polledMessages.forEach { (key, messages) ->
                assertThat(messages)
                    .usingRecursiveComparison()
                    .ignoringFields("status", "lastDelivered", "deliveredTimes")
                    .isEqualTo(allMessageLog.filter { it.routing() == key }.sortedBy { it.timestamp })
            }
        }

    @Test
    fun `should mark multiple messages as failed in bulk`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer)
        val message2 = publishMessage(channel, producer)
        val message3 = publishMessage(channel, producer)

        pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 3)

        val result = pollingQueue.markAsFailedBulk(listOf(message1.messageId, message2.messageId, message3.messageId))

        assertThat(result.processed).isEqualTo(listOf(message1.messageId, message2.messageId, message3.messageId))

        val messages = messageRepository.findAll()
        assertThat(messages).isEmpty()

        val dlqMessages = dlqRepository.findAllByChannelIdAndRoutingKey(channel.channelId, channel.routingKeys.first())
        assertThat(dlqMessages).hasSize(3)
    }

    @Test
    fun `should return not found IDs when marking non-existent messages as failed in bulk`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer)
        val nonExistentId = UUID.randomUUID()

        pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 1)

        val result = pollingQueue.markAsFailedBulk(listOf(message1.messageId, nonExistentId))

        assertThat(result.processed).isEqualTo(listOf(message1.messageId))
    }

    @Test
    fun `should return empty result when marking empty list as failed in bulk`() {
        val result = pollingQueue.markAsFailedBulk(emptyList())

        assertThat(result.processed).isEqualTo(emptyList<UUID>())
    }

    @Test
    fun `should return all not found when all messages do not exist for mark as failed bulk`() {
        val nonExistentId1 = UUID.randomUUID()
        val nonExistentId2 = UUID.randomUUID()

        val result = pollingQueue.markAsFailedBulk(listOf(nonExistentId1, nonExistentId2))

        assertThat(result.processed).isEqualTo(emptyList<UUID>())
    }

    @Test
    fun `should mark messages from different channels as failed in bulk`() {
        val (channel1, producer1) = createChannelWithProducer()
        val (channel2, producer2) = createChannelWithProducer()
        val message1 = publishMessage(channel1, producer1)
        val message2 = publishMessage(channel2, producer2)

        pollingQueue.poll(channel1.channelId!!, channel1.routingKeys.first(), 1)
        pollingQueue.poll(channel2.channelId!!, channel2.routingKeys.first(), 1)

        val result = pollingQueue.markAsFailedBulk(listOf(message1.messageId, message2.messageId))

        assertThat(result.processed).isEqualTo(listOf(message1.messageId, message2.messageId))

        val dlq1 = dlqRepository.findAllByChannelIdAndRoutingKey(channel1.channelId, channel1.routingKeys.first())
        val dlq2 = dlqRepository.findAllByChannelIdAndRoutingKey(channel2.channelId, channel2.routingKeys.first())
        assertThat(dlq1).hasSize(1)
        assertThat(dlq2).hasSize(1)
    }

    @Test
    fun `should dequeue multiple messages in bulk`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer)
        val message2 = publishMessage(channel, producer)
        val message3 = publishMessage(channel, producer)

        pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 3)

        val result = pollingQueue.dequeueBulk(listOf(message1.messageId, message2.messageId, message3.messageId))

        assertThat(result.processed).isEqualTo(listOf(message1.messageId, message2.messageId, message3.messageId))

        val messages = messageRepository.findAll()
        assertThat(messages).isEmpty()

        val messageLogs = messageRepository.findAllMessageLog()
        assertThat(messageLogs).hasSize(3)
        assertThat(messageLogs.all { it.processed }).isTrue
    }

    @Test
    fun `should return not found IDs when dequeuing non-existent messages in bulk`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer)
        val nonExistentId = UUID.randomUUID()

        pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 1)

        val result = pollingQueue.dequeueBulk(listOf(message1.messageId, nonExistentId))

        assertThat(result.processed).isEqualTo(listOf(message1.messageId))
    }

    @Test
    fun `should return empty result when dequeuing empty list in bulk`() {
        val result = pollingQueue.dequeueBulk(emptyList())

        assertThat(result.processed).isEqualTo(emptyList<UUID>())
    }

    @Test
    fun `should return all not found when all messages do not exist for dequeue bulk`() {
        val nonExistentId1 = UUID.randomUUID()
        val nonExistentId2 = UUID.randomUUID()

        val result = pollingQueue.dequeueBulk(listOf(nonExistentId1, nonExistentId2))

        assertThat(result.processed).isEqualTo(emptyList<UUID>())
    }

    @Test
    fun `should not dequeue any messages when all are in READY status in bulk`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer)
        val message2 = publishMessage(channel, producer)

        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy {
                pollingQueue.dequeueBulk(listOf(message1.messageId, message2.messageId))
            }.withMessageContaining("Some Messages", "are still in status READY")

        val messages = messageRepository.findAll()
        assertThat(messages).hasSize(2)
    }

    @Test
    fun `should dequeue messages from different channels in bulk`() {
        val (channel1, producer1) = createChannelWithProducer()
        val (channel2, producer2) = createChannelWithProducer()
        val message1 = publishMessage(channel1, producer1)
        val message2 = publishMessage(channel2, producer2)

        pollingQueue.poll(channel1.channelId!!, channel1.routingKeys.first(), 1)
        pollingQueue.poll(channel2.channelId!!, channel2.routingKeys.first(), 1)

        val result = pollingQueue.dequeueBulk(listOf(message1.messageId, message2.messageId))

        assertThat(result.processed).isEqualTo(listOf(message1.messageId, message2.messageId))

        val messages = messageRepository.findAll()
        assertThat(messages).isEmpty()
    }

    @Test
    fun `should handle mixed scenarios in bulk mark as failed - found and not found`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer)
        val message2 = publishMessage(channel, producer)
        val nonExistentId = UUID.randomUUID()

        pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 2)

        val result = pollingQueue.markAsFailedBulk(listOf(message1.messageId, message2.messageId, nonExistentId))

        assertThat(result.processed).isEqualTo(listOf(message1.messageId, message2.messageId))
    }

    @Test
    fun `should correctly process bulk dequeue with duplicate message IDs`() {
        val (channel, producer) = createChannelWithProducer()
        val message = publishMessage(channel, producer)

        pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 1)

        val result = pollingQueue.dequeueBulk(listOf(message.messageId, message.messageId))

        assertThat(result.processed).isEqualTo(listOf(message.messageId))

        val messages = messageRepository.findAll()
        assertThat(messages).isEmpty()
    }

    @Test
    fun `should correctly process bulk mark as failed with duplicate message IDs`() {
        val (channel, producer) = createChannelWithProducer()
        val message = publishMessage(channel, producer)

        pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 1)

        val result = pollingQueue.markAsFailedBulk(listOf(message.messageId, message.messageId))

        assertThat(result.processed).isEqualTo(listOf(message.messageId))
    }
}
