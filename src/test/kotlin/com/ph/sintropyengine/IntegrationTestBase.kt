package com.ph.sintropyengine

import com.ph.sintropyengine.broker.channel.model.Channel
import com.ph.sintropyengine.broker.channel.model.ChannelLink
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE
import com.ph.sintropyengine.broker.channel.model.ChannelType.STREAM
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO
import com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD
import com.ph.sintropyengine.broker.channel.repository.ChannelLinkRepository
import com.ph.sintropyengine.broker.channel.repository.ChannelRepository
import com.ph.sintropyengine.broker.channel.service.ChannelLinkService
import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.repository.CircuitBreakerRepository
import com.ph.sintropyengine.broker.consumption.repository.DeadLetterQueueRepository
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.consumption.service.CircuitBreakerService
import com.ph.sintropyengine.broker.consumption.service.MessageRecoveryService
import com.ph.sintropyengine.broker.consumption.service.PollingQueue
import com.ph.sintropyengine.broker.iac.repository.IaCRepository
import com.ph.sintropyengine.broker.producer.model.Producer
import com.ph.sintropyengine.broker.producer.repository.ProducerRepository
import com.ph.sintropyengine.broker.producer.service.ProducerService
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.test.common.QuarkusTestResource
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

@QuarkusTestResource(PostgresqlDBTestResource::class)
open class IntegrationTestBase {
    @Inject
    protected lateinit var messageRepository: MessageRepository

    @Inject
    protected lateinit var dlqRepository: DeadLetterQueueRepository

    @Inject
    protected lateinit var channelRepository: ChannelRepository

    @Inject
    protected lateinit var channelLinkRepository: ChannelLinkRepository

    @Inject
    protected lateinit var producerRepository: ProducerRepository

    @Inject
    protected lateinit var producerService: ProducerService

    @Inject
    protected lateinit var channelService: ChannelService

    @Inject
    protected lateinit var channelLinkService: ChannelLinkService

    @Inject
    protected lateinit var iaCRepository: IaCRepository

    @Inject
    protected lateinit var messageRecoveryService: MessageRecoveryService

    @Inject
    protected lateinit var circuitBreakerRepository: CircuitBreakerRepository

    @Inject
    protected lateinit var circuitBreakerService: CircuitBreakerService

    protected fun clean() {
        circuitBreakerRepository.deleteAll()
        dlqRepository.deleteAll()
        messageRepository.deleteAll()
        producerRepository.deleteAll()
        channelLinkRepository.deleteAll()
        channelRepository.deleteAll()
    }

    protected fun createChannel(
        channelType: ChannelType = QUEUE,
        consumptionType: ConsumptionType? = if (channelType == QUEUE) STANDARD else null,
        name: String = UUID.randomUUID().toString(),
    ): Channel {
        val channel = Fixtures.createChannel(name = name, channelType = channelType, consumptionType = consumptionType)
        return channelRepository.save(channel)
    }

    protected fun createStandardQueueChannel(): Channel = createChannel(QUEUE, STANDARD)

    protected fun createFifoQueueChannel(): Channel = createChannel(QUEUE, FIFO)

    protected fun createStreamChannel(): Channel = createChannel(channelType = STREAM)

    protected fun createChannelLink(
        sourceChannel: Channel,
        targetChannel: Channel,
        sourceRoutingKey: String = sourceChannel.routingKeys.first(),
        targetRoutingKey: String = targetChannel.routingKeys.first(),
    ): ChannelLink =
        channelLinkRepository.save(
            ChannelLink(
                sourceChannelId = sourceChannel.channelId!!,
                targetChannelId = targetChannel.channelId!!,
                sourceRoutingKey = sourceRoutingKey,
                targetRoutingKey = targetRoutingKey,
            ),
        )

    protected fun createProducer(): Producer = producerRepository.save(Fixtures.createProducer())

    protected fun createChannelWithProducer(consumptionType: ConsumptionType = STANDARD): Pair<Channel, Producer> {
        val channel = createChannel(consumptionType = consumptionType)
        val producer = createProducer()
        return Pair(channel, producer)
    }

    /**
     * Publishes a messages to the target channel from the target producer
     * The new message entry have a unique ID
     */
    protected fun publishMessage(
        channel: Channel,
        producer: Producer,
        routingKey: String = channel.routingKeys.first(),
    ): Message =
        producerService.publishMessage(
            Fixtures.createMessagePreStore(
                channel.name,
                producer.name,
                routingKey,
            ),
        )

    /**
     * Publishes a messages to freshly created channels from freshly created producers
     * Each new entry created have a unique ID
     */
    protected fun publishMessage(consumptionType: ConsumptionType = STANDARD): Message {
        val (channel, producer) = createChannelWithProducer(consumptionType = consumptionType)

        return producerService.publishMessage(
            Fixtures.createMessagePreStore(
                channel.name,
                producer.name,
                channel.routingKeys.first(),
            ),
        )
    }

    protected fun launchProducers(testData: TestData) {
        logger.debug { "launching new producer..." }
        val message =
            when (Random.nextInt(1, 4)) {
                1 -> {
                    Fixtures.createMessagePreStore(
                        channelName = testData.channel1().name,
                        routingKey = testData.channel1().routingKeys[Random.nextInt(0, 3)],
                        producerName = testData.producer1().name,
                    )
                }

                2 -> {
                    Fixtures.createMessagePreStore(
                        channelName = testData.channel2().name,
                        routingKey = testData.channel2().routingKeys[Random.nextInt(0, 2)],
                        producerName = testData.producer2().name,
                    )
                }

                3 -> {
                    Fixtures.createMessagePreStore(
                        channelName = testData.channel3().name,
                        routingKey = testData.channel3().routingKeys.first(),
                        producerName = testData.producer3().name,
                    )
                }

                else -> {
                    throw IllegalStateException("Failure setting test data")
                }
            }
        producerService.publishMessage(message)
    }

    protected suspend fun launchConsumers(
        channelId: UUID,
        routingKey: String,
        channel: kotlinx.coroutines.channels.Channel<Message>,
        pollingQueue: PollingQueue,
    ) {
        logger.info { "launching new consumer for [${routing(channelId, routingKey)}]" }
        var counter = 0
        while (true) {
            val messages = pollingQueue.poll(channelId, routingKey, pollingCount = Random.nextInt(1, 6))

            if (messages.isEmpty()) {
                if (counter > 5) {
                    logger.info { "Consumer finished polling work for [${routing(channelId, routingKey)}]" }
                    break
                }

                counter++
                delay(20)
                continue
            }

            val routingInMessages = messages.map { it.routing() }.toSet()

            if (routingInMessages.size > 1 ||
                routingInMessages.first() != routing(channelId, routingKey)
            ) {
                throw IllegalStateException(
                    "Polling got a message form another pair [${routing(channelId, routingKey)}]",
                )
            }

            messages.forEach { channel.send(it) }

            messages.forEach {
                pollingQueue.dequeue(it.messageId)
            }

            delay(10)
        }
    }

    protected fun createTestData(consumptionType: ConsumptionType): TestData {
        val channel1 =
            channelRepository.save(
                Fixtures.createChannel(
                    consumptionType = consumptionType,
                    routingKeys = mutableListOf("test.1.0", "test.1.1", "test.1.2"),
                ),
            )
        val channel2 =
            channelRepository.save(
                Fixtures.createChannel(
                    consumptionType = consumptionType,
                    routingKeys = mutableListOf("test.2.0", "test.2.1"),
                ),
            )
        val channel3 =
            channelRepository.save(
                Fixtures.createChannel(
                    consumptionType = consumptionType,
                    routingKeys = mutableListOf("test.3.0"),
                ),
            )
        val producer1 = producerRepository.save(Fixtures.createProducer(channel1.channelId!!))
        val producer2 = producerRepository.save(Fixtures.createProducer(channel2.channelId!!))
        val producer3 = producerRepository.save(Fixtures.createProducer(channel3.channelId!!))

        return TestData(
            channelProducer1 = ChannelProducer(channel1, producer1),
            channelProducer2 = ChannelProducer(channel2, producer2),
            channelProducer3 = ChannelProducer(channel3, producer3),
        )
    }
}

data class ChannelProducer(
    val channel: Channel,
    val producer: Producer,
)

data class TestData(
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

    fun routing10() = routing(channelProducer1.channel.channelId!!, channelProducer1.channel.routingKeys[0])

    fun routing11() = routing(channelProducer1.channel.channelId!!, channelProducer1.channel.routingKeys[1])

    fun routing12() = routing(channelProducer1.channel.channelId!!, channelProducer1.channel.routingKeys[2])

    fun routing20() = routing(channelProducer2.channel.channelId!!, channelProducer2.channel.routingKeys[0])

    fun routing21() = routing(channelProducer2.channel.channelId!!, channelProducer2.channel.routingKeys[1])

    fun routing30() = routing(channelProducer3.channel.channelId!!, channelProducer3.channel.routingKeys[0])

    fun toMap(): MutableMap<String, MutableList<Message>> =
        mutableMapOf(
            routing10() to mutableListOf(),
            routing11() to mutableListOf(),
            routing12() to mutableListOf(),
            routing20() to mutableListOf(),
            routing21() to mutableListOf(),
            routing30() to mutableListOf(),
        )
}
