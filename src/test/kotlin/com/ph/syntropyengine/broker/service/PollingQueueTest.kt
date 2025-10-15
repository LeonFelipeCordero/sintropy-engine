package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.Fixtures
import com.ph.syntropyengine.IntegrationTestBase
import com.ph.syntropyengine.broker.model.Channel
import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.model.Producer
import com.ph.syntropyengine.utils.Patterns.loggingPair
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.random.Random
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

private val logger = KotlinLogging.logger {}

class PollingQueueTest : IntegrationTestBase() {

    @Autowired
    private lateinit var pollingQueue: PollingQueue

    @Autowired
    private lateinit var channelService: ChannelService

    @Autowired
    private lateinit var producerService: ProducerService

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

        val mutexProducer = Mutex()
        val publishedMessages = mutableListOf<Message>()

        val mutexConsumer = Mutex()
        val polledMessages = mutableListOf<Message>()

        val launchConsumer: suspend (channelId: UUID, routingKey: String) -> Unit = { channelId, routingKey ->
            val messages = launchConsumers(channelId, routingKey)
            mutexConsumer.withLock { polledMessages.addAll(messages) }
        }

        val allWork = launch {
            val producersJobs = List(5000) {
                launch {
                    val message = launchProducers(testData)
                    mutexProducer.withLock {
                        publishedMessages.add(message)
                    }
                }
            }

            producersJobs.joinAll()

            delay(1000)

            val consumersJobs = mutableListOf<Job>()
            repeat(10) {
                consumersJobs.addAll(
                    listOf(
                        launch { launchConsumer(testData.channel1().channelId!!, testData.channel1().routingKeys[0]) },
                        launch { launchConsumer(testData.channel1().channelId!!, testData.channel1().routingKeys[1]) },
                        launch { launchConsumer(testData.channel1().channelId!!, testData.channel1().routingKeys[2]) },
                        launch { launchConsumer(testData.channel2().channelId!!, testData.channel2().routingKeys[0]) },
                        launch { launchConsumer(testData.channel2().channelId!!, testData.channel2().routingKeys[1]) },
                        launch { launchConsumer(testData.channel3().channelId!!, testData.channel3().routingKeys[0]) }
                    )
                )
            }

            consumersJobs.joinAll()
        }

        allWork.join()

        assertThat(publishedMessages).hasSize(5000)
        assertThat(polledMessages).hasSize(5000)

        assertThat(publishedMessages.map { it.messageId }.sorted())
            .isEqualTo(polledMessages.map { it.messageId }.sorted())
    }

    @Test
    fun `single processing of routing pair is always in chronological order`() = runTest {
        val testData = createTestData()

        val mutexProducer = Mutex()
        val publishedMessages = mutableMapOf<String, MutableList<Message>>(
            routing(testData.channel1().channelId!!, testData.channel1().routingKeys[0]) to mutableListOf(),
            routing(testData.channel1().channelId!!, testData.channel1().routingKeys[1]) to mutableListOf(),
            routing(testData.channel1().channelId!!, testData.channel1().routingKeys[2]) to mutableListOf(),
            routing(testData.channel2().channelId!!, testData.channel2().routingKeys[0]) to mutableListOf(),
            routing(testData.channel2().channelId!!, testData.channel2().routingKeys[1]) to mutableListOf(),
            routing(testData.channel3().channelId!!, testData.channel3().routingKeys[0]) to mutableListOf(),
        )

        val mutexConsumer = Mutex()
        val polledMessages = mutableMapOf<String, MutableList<Message>>(
            routing(testData.channel1().channelId!!, testData.channel1().routingKeys[0]) to mutableListOf(),
            routing(testData.channel1().channelId!!, testData.channel1().routingKeys[1]) to mutableListOf(),
            routing(testData.channel1().channelId!!, testData.channel1().routingKeys[2]) to mutableListOf(),
            routing(testData.channel2().channelId!!, testData.channel2().routingKeys[0]) to mutableListOf(),
            routing(testData.channel2().channelId!!, testData.channel2().routingKeys[1]) to mutableListOf(),
            routing(testData.channel3().channelId!!, testData.channel3().routingKeys[0]) to mutableListOf(),
        )

        val launchConsumer: suspend (channelId: UUID, routingKey: String) -> Unit = { channelId, routingKey ->
            val messages = launchConsumers(channelId, routingKey)
            mutexConsumer.withLock {
                polledMessages[routing(channelId, routingKey)]!!.addAll(messages)
            }
        }

        val allWork = launch {
            val producersJobs = List(5000) {
                launch {
                    val message = launchProducers(testData)
                    mutexProducer.withLock {
                        publishedMessages[message.routing()]!!.add(message)
                    }
                }
            }

            producersJobs.joinAll()

            delay(1000)

            val consumersJobs = listOf(
                launch { launchConsumer(testData.channel1().channelId!!, testData.channel1().routingKeys[0]) },
                launch { launchConsumer(testData.channel1().channelId!!, testData.channel1().routingKeys[1]) },
                launch { launchConsumer(testData.channel1().channelId!!, testData.channel1().routingKeys[2]) },
                launch { launchConsumer(testData.channel2().channelId!!, testData.channel2().routingKeys[0]) },
                launch { launchConsumer(testData.channel2().channelId!!, testData.channel2().routingKeys[1]) },
                launch { launchConsumer(testData.channel3().channelId!!, testData.channel3().routingKeys[0]) }
            )

            consumersJobs.joinAll()
        }

        allWork.join()

        publishedMessages.forEach { (key, messages) ->
            assertThat(messages)
                .usingRecursiveComparison()
                .ignoringFields("status", "lastDelivered", "deliveredTimes")
                .isEqualTo(polledMessages[key])
        }
    }

    private suspend fun launchProducers(testData: TestData): Message {
        logger.info { "launching new producer..." }
        val message = when (Random.nextInt(1, 4)) {
            1 -> Fixtures.createMessage(
                channelId = testData.channel1().channelId!!,
                routingKey = testData.channel1().routingKeys[Random.nextInt(0, 3)],
                producerId = testData.producer1().producerId!!,
            )

            2 -> Fixtures.createMessage(
                channelId = testData.channel2().channelId!!,
                routingKey = testData.channel2().routingKeys[Random.nextInt(0, 2)],
                producerId = testData.producer2().producerId!!
            )

            3 -> Fixtures.createMessage(
                channelId = testData.channel3().channelId!!,
                routingKey = testData.channel3().routingKeys.first(),
                producerId = testData.producer3().producerId!!
            )

            else -> {
                throw IllegalStateException("Failure setting test data")
            }
        }

        return producerService.publishMessage(message)
    }

    private suspend fun launchConsumers(channelId: UUID, routingKey: String): MutableList<Message> {
        logger.info { "launching new consumer for ${loggingPair(channelId, routingKey)}" }
        var counter = 0
        val totalMessages = mutableListOf<Message>()
        while (true) {
            val messages = pollingQueue.poll(channelId, routingKey, pollingCount = Random.nextInt(1, 6))

            if (messages.isEmpty()) {
                if (counter > 5) {
                    logger.info { "Consumer finished polling work for ${loggingPair(channelId, routingKey)}" }
                    break
                }

                counter++
                delay(20)
                continue
            }

            val routingInMessages = messages.map { it.routing() }.toSet()
            if (routingInMessages.size > 1 || routingInMessages.first() != routing(channelId, routingKey)) {
                throw IllegalStateException(
                    "Polling got a message form another pair ${
                        loggingPair(
                            channelId,
                            routingKey
                        )
                    }"
                )
            }

            totalMessages.addAll(messages)

            messages.forEach {
                pollingQueue.dequeue(it.messageId)
            }

            delay(10)

        }
        return totalMessages
    }

    private fun createTestData(): TestData {
        val channel1 = channelRepository.save(
            Fixtures.createChannel(routingKeys = mutableListOf("test.1.1", "test.1.2", "test.1.3"))
        )
        val channel2 = channelRepository.save(
            Fixtures.createChannel(routingKeys = mutableListOf("test.2.1", "test.2.2"))
        )
        val channel3 = channelRepository.save(Fixtures.createChannel(routingKeys = mutableListOf("test.3.1")))
        val producer1 = producerRepository.save(Fixtures.createProducer(channel1.channelId!!))
        val producer2 = producerRepository.save(Fixtures.createProducer(channel2.channelId!!))
        val producer3 = producerRepository.save(Fixtures.createProducer(channel3.channelId!!))

        return TestData(
            ChannelProducer(channel1, producer1),
            ChannelProducer(channel2, producer2),
            ChannelProducer(channel3, producer3)
        )
    }

    private data class TestData(
        private val channelProducer1: ChannelProducer,
        private val channelProducer2: ChannelProducer,
        private val channelProducer3: ChannelProducer,
    ) {
        fun channel1() = channelProducer1.channel
        fun channel2() = channelProducer2.channel
        fun channel3() = channelProducer3.channel
        fun producer1() = channelProducer1.producer
        fun producer2() = channelProducer2.producer
        fun producer3() = channelProducer3.producer
    }

    private data class ChannelProducer(
        val channel: Channel,
        val producer: Producer
    )
}
