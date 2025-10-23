package com.ph.syntropyengine

import com.ph.syntropyengine.broker.model.Channel
import com.ph.syntropyengine.broker.model.ChannelType
import com.ph.syntropyengine.broker.model.ChannelType.*
import com.ph.syntropyengine.broker.model.Consumer
import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.model.Producer
import com.ph.syntropyengine.broker.repository.ChannelRepository
import com.ph.syntropyengine.broker.repository.ConsumerRepository
import com.ph.syntropyengine.broker.repository.MessageRepository
import com.ph.syntropyengine.broker.repository.ProducerRepository
import com.ph.syntropyengine.broker.service.PollingQueue
import com.ph.syntropyengine.broker.service.ProducerService
import com.ph.syntropyengine.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.delay
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName

private val logger = KotlinLogging.logger {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["syen.feature-flags.with-full-replication=false"]
)
abstract class IntegrationTestBase {

    @Autowired
    protected lateinit var messageRepository: MessageRepository

    @Autowired
    protected lateinit var channelRepository: ChannelRepository

    @Autowired
    protected lateinit var consumerRepository: ConsumerRepository

    @Autowired
    protected lateinit var producerRepository: ProducerRepository

    @Autowired
    protected lateinit var producerService: ProducerService

    protected fun clean() {
        messageRepository.deleteAll()
        consumerRepository.deleteAll()
        producerRepository.deleteAll()
        channelRepository.deleteAll()
    }

    /**
     * Create a new channel and new consumer with unique ID
     */
    protected fun createChannelAndConsumer(): Pair<Channel, Consumer> {
        val channel = createChannel()
        val consumer = consumerRepository.save(Fixtures.createConsumer(channel.channelId!!))
        return Pair(channel, consumer)
    }

    /**
     * Create a new channel with unique IDs
     */
    protected fun createChannel(channelType: ChannelType = STANDARD): Channel {
        val channel = Fixtures.createChannel(channelType = channelType)
        return channelRepository.save(channel)
    }

    /**
     * Create a new channel and a new producer with unique IDs
     */
    protected fun createChannelWithProducer(channelType: ChannelType = STANDARD): Pair<Channel, Producer> {
        val channel = createChannel(channelType = channelType)
        val consumer = producerRepository.save(Fixtures.createProducer(channel.channelId!!))
        return Pair(channel, consumer)
    }

    /**
     * Publishes a messages to the target channel from the target producer
     * The new message entry have a unique ID
     */
    protected fun publishMessage(
        channel: Channel,
        producer: Producer,
        routingKey: String = channel.routingKeys.first(),
        timestamp: OffsetDateTime = OffsetDateTime.now()
    ): Message {
        return messageRepository.save(
            Fixtures.createMessage(
                channelId = channel.channelId!!,
                producerId = producer.producerId!!,
                routingKey = routingKey,
                timestamp = timestamp
            )
        )
    }

    /**
     * Publishes a messages to freshly created channels from freshly created producers
     * Each new entry created have a unique ID
     */
    protected fun publishMessage(channelType: ChannelType = STANDARD): Message {
        val (channel, producer) = createChannelWithProducer(channelType = channelType)

        return messageRepository.save(
            Fixtures.createMessage(
                channel.channelId!!,
                producer.producerId!!,
                channel.routingKeys.first()
            )
        )
    }

    protected suspend fun launchProducers(testData: TestData) {
        logger.debug { "launching new producer..." }
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
        producerService.publishMessage(message)
    }

    protected suspend fun launchConsumers(
        channelId: UUID,
        routingKey: String,
        channel: kotlinx.coroutines.channels.Channel<Message>,
        pollingQueue: PollingQueue
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

            val routingInMessages = messages.map { it.routing() }
                .toSet(); if (routingInMessages.size > 1 || routingInMessages.first() != routing(
                    channelId,
                    routingKey
                )
            ) {
                throw IllegalStateException(
                    "Polling got a message form another pair [${routing(channelId, routingKey)}]"
                )
            }

            messages.forEach { channel.send(it) }

            messages.forEach {
                pollingQueue.dequeue(it.messageId)
            }

            delay(10)
        }
    }

    protected fun createTestData(): TestData {
        val channel1 = channelRepository.save(
            Fixtures.createChannel(
                channelType = FIFO,
                routingKeys = mutableListOf("test.1.0", "test.1.1", "test.1.2")
            )
        )
        val channel2 = channelRepository.save(
            Fixtures.createChannel(
                channelType = FIFO,
                routingKeys = mutableListOf("test.2.0", "test.2.1")
            )
        )
        val channel3 = channelRepository.save(
            Fixtures.createChannel(
                channelType = FIFO,
                routingKeys = mutableListOf("test.3.0")
            )
        )
        val producer1 = producerRepository.save(Fixtures.createProducer(channel1.channelId!!))
        val producer2 = producerRepository.save(Fixtures.createProducer(channel2.channelId!!))
        val producer3 = producerRepository.save(Fixtures.createProducer(channel3.channelId!!))

        return TestData(
            channelProducer1 = ChannelProducer(channel1, producer1),
            channelProducer2 = ChannelProducer(channel2, producer2),
            channelProducer3 = ChannelProducer(channel3, producer3)
        )
    }


    companion object {
        const val DB_USER = "postgres"
        const val DB_PASSWORD = "postgres"
        const val DB_NAME = "postgres"

        val customerImage: ImageFromDockerfile = ImageFromDockerfile("development-postgres")
            .withDockerfile(File("development/postgres17-wal2json/Dockerfile").toPath())

        @Container
        val container: PostgreSQLContainer<*> =
            PostgreSQLContainer(
                DockerImageName.parse(customerImage.get())
                    .asCompatibleSubstituteFor("postgres")
            )
                // TODO the image is not taking default configurations from docker image
                // try in the future to push to a container registry and pull from there
                .withCreateContainerCmdModifier {
                    it.withCmd(
                        *it.cmd,
                        "-c",
                        "wal_level=logical",
                        "-c",
                        "max_wal_senders=15",
                        "-c",
                        "max_replication_slots=15",
                        "-c",
                        "max_logical_replication_workers=15",
                        "-c",
                        "max_worker_processes=16",
                        "-c",
                        "hba_file=/etc/postgresql/pg_hba.conf"
                    )
                }
                .withReuse(true)
                .withUsername(DB_USER)
                .withPassword(DB_PASSWORD)
                .withDatabaseName(DB_NAME)
                .apply { start() }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { DB_USER }
            registry.add("spring.datasource.password") { DB_PASSWORD }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }

            registry.add("spring.liquibase.url") { container.jdbcUrl }
            registry.add("spring.liquibase.user") { DB_USER }
            registry.add("spring.liquibase.password") { DB_PASSWORD }
            registry.add("spring.liquibase.driver-class-name") { "org.postgresql.Driver" }
        }
    }
}

data class ChannelProducer(
    val channel: Channel,
    val producer: Producer
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

    fun toMap(): MutableMap<String, MutableList<Message>> {
        return mutableMapOf(
            routing10() to mutableListOf(),
            routing11() to mutableListOf(),
            routing12() to mutableListOf(),
            routing20() to mutableListOf(),
            routing21() to mutableListOf(),
            routing30() to mutableListOf(),
        )
    }
}