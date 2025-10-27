package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.Fixtures
import com.ph.syntropyengine.IntegrationTestBase
import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.utils.Patterns.routing
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.Job
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
import org.springframework.beans.factory.annotation.Autowired

class PollingStandardQueueTest : IntegrationTestBase() {

    @Autowired
    private lateinit var pollingQueue: PollingStandardQueue

    @Autowired
    private lateinit var channelService: ChannelService

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
    fun `should not return anything if the message is consumed`() = runTest {
        val message = publishMessage()

        pollingQueue.poll(message.channelId, message.routingKey)
        val polledMessage = pollingQueue.poll(message.channelId, message.routingKey)

        assertThat(polledMessage).isEmpty()
    }

    @Test
    fun `should queue two messages and poll one by one`() = runTest {
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
    fun `should queue a message and try to poll five`() = runTest {
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
        val message1 = publishMessage(channel, producer, timestamp = OffsetDateTime.now().minusSeconds(5))
        val message2 = publishMessage(channel, producer, timestamp = OffsetDateTime.now().minusSeconds(10))
        val message3 = publishMessage(channel, producer, timestamp = OffsetDateTime.now().minusSeconds(15))

        val polledMessage1 = pollingQueue.poll(channel.channelId!!, channel.routingKeys.first())
        val polledMessage2 = pollingQueue.poll(channel.channelId, channel.routingKeys.first())
        val polledMessage3 = pollingQueue.poll(channel.channelId, channel.routingKeys.first())

        assertThat(polledMessage1).hasSize(1)
        assertThat(polledMessage2).hasSize(1)
        assertThat(polledMessage3).hasSize(1)

        assertThat(polledMessage1.first())
            .usingRecursiveComparison()
            .ignoringFields("status", "lastDelivered", "deliveredTimes")
            .isEqualTo(message3)
        assertThat(polledMessage2.first())
            .usingRecursiveComparison()
            .ignoringFields("status", "lastDelivered", "deliveredTimes")
            .isEqualTo(message2)
        assertThat(polledMessage3.first())
            .usingRecursiveComparison()
            .ignoringFields("status", "lastDelivered", "deliveredTimes")
            .isEqualTo(message1)
    }

    @Test
    fun `should poll messages in chronological order all at once`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(channel, producer, timestamp = OffsetDateTime.now().minusSeconds(15))
        val message2 = publishMessage(channel, producer, timestamp = OffsetDateTime.now().minusSeconds(17))
        val message3 = publishMessage(channel, producer, timestamp = OffsetDateTime.now().minusSeconds(5))

        val polledMessages = pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 3)

        assertThat(polledMessages).hasSize(3)
        assertThat(polledMessages.map { it.messageId }).isEqualTo(
            listOf(
                message2.messageId,
                message1.messageId,
                message3.messageId
            )
        )
    }

    @Test
    fun `should not poll messages from other routing key`() {
        val (channel, producer) = createChannelWithProducer()
        val message1 = publishMessage(
            channel,
            producer,
            channel.routingKeys.first(),
        )

        val secondRoutingKey = "test.2"
        channelService.addRoutingKey(channel.channelId!!, secondRoutingKey)
        val message2 = publishMessage(
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
    fun `should dequeue a message that is processed and mark it in the event log`() {
        val message = publishMessage()

        pollingQueue.poll(message.channelId, message.routingKey)

        pollingQueue.dequeue(message.messageId)

        val messages = messageRepository.findAll()
        val eventLogs = messageRepository.findAllEventLog()

        assertThat(messages).hasSize(0)
        assertThat(eventLogs).hasSize(1)
        assertThat(eventLogs.first().processed).isTrue
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
    fun `concurrent processing of messages`() = runTest {
        val testData = createTestData()

        val mutexConsumer = Mutex()
        val polledMessages = testData.toMap()

        val messagesCount = 1000
        val allWork = launch {
            val producersJobs = List(messagesCount) {
                launch { launchProducers(testData) }
            }

            producersJobs.joinAll()

            val consumersJobs = mutableListOf<Job>()
            val pollingChannel = kotlinx.coroutines.channels.Channel<Message>(capacity = messagesCount)
            repeat(10) {
                consumersJobs.addAll(
                    listOf(
                        launch {
                            launchConsumers(
                                testData.channel1().channelId!!,
                                testData.channel1().routingKeys[0],
                                pollingChannel,
                                pollingQueue
                            )
                        },
                        launch {
                            launchConsumers(
                                testData.channel1().channelId!!,
                                testData.channel1().routingKeys[1],
                                pollingChannel,
                                pollingQueue
                            )
                        },
                        launch {
                            launchConsumers(
                                testData.channel1().channelId!!,
                                testData.channel1().routingKeys[2],
                                pollingChannel,
                                pollingQueue
                            )
                        },
                        launch {
                            launchConsumers(
                                testData.channel2().channelId!!,
                                testData.channel2().routingKeys[0],
                                pollingChannel,
                                pollingQueue
                            )
                        },
                        launch {
                            launchConsumers(
                                testData.channel2().channelId!!,
                                testData.channel2().routingKeys[1],
                                pollingChannel,
                                pollingQueue
                            )
                        },
                        launch {
                            launchConsumers(
                                testData.channel3().channelId!!,
                                testData.channel3().routingKeys[0],
                                pollingChannel,
                                pollingQueue
                            )
                        }
                    )
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

        val allEventLog = messageRepository.findAllEventLog().filter { it.processed }
        assertThat(allEventLog).hasSize(messagesCount)
        assertThat(polledMessages.flatMap { (_, value) -> value }).hasSize(messagesCount)

        polledMessages.forEach { (key, messages) ->
            assertThat(messages)
                .usingRecursiveComparison()
                .ignoringFields("status", "lastDelivered", "deliveredTimes")
                .isEqualTo(allEventLog.filter { it.routing() == key }.sortedBy { it.timestamp })
        }
    }

    @Test
    fun `single processing of routing pair is always in chronological order`() = runTest {
        val testData = createTestData()

        val mutexConsumer = Mutex()
        val polledMessages = testData.toMap()

        val messagesCount = 1000
        val allWork = launch {
            val producersJobs = List(messagesCount) {
                launch { launchProducers(testData) }
            }

            producersJobs.joinAll()

            delay(1000)

            val pollingChannel = kotlinx.coroutines.channels.Channel<Message>(capacity = messagesCount)
            val consumersJobs = listOf(
                launch {
                    launchConsumers(
                        testData.channel1().channelId!!,
                        testData.channel1().routingKeys[0],
                        pollingChannel,
                        pollingQueue
                    )
                },
                launch {
                    launchConsumers(
                        testData.channel1().channelId!!,
                        testData.channel1().routingKeys[1],
                        pollingChannel,
                        pollingQueue
                    )
                },
                launch {
                    launchConsumers(
                        testData.channel1().channelId!!,
                        testData.channel1().routingKeys[2],
                        pollingChannel,
                        pollingQueue
                    )
                },
                launch {
                    launchConsumers(
                        testData.channel2().channelId!!,
                        testData.channel2().routingKeys[0],
                        pollingChannel,
                        pollingQueue
                    )
                },
                launch {
                    launchConsumers(
                        testData.channel2().channelId!!,
                        testData.channel2().routingKeys[1],
                        pollingChannel,
                        pollingQueue
                    )
                },
                launch {
                    launchConsumers(
                        testData.channel3().channelId!!,
                        testData.channel3().routingKeys[0],
                        pollingChannel,
                        pollingQueue
                    )
                }
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

        val allEventLog = messageRepository.findAllEventLog().filter { it.processed }
        assertThat(allEventLog).hasSize(messagesCount)
        assertThat(polledMessages.flatMap { (_, value) -> value }).hasSize(messagesCount)

        polledMessages.forEach { (key, messages) ->
            assertThat(messages)
                .usingRecursiveComparison()
                .ignoringFields("status", "lastDelivered", "deliveredTimes")
                .isEqualTo(allEventLog.filter { it.routing() == key }.sortedBy { it.timestamp })
        }
    }
}
