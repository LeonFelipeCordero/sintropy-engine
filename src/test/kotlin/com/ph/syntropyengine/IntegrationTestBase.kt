package com.ph.syntropyengine

import com.ph.syntropyengine.broker.model.Channel
import com.ph.syntropyengine.broker.model.Consumer
import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.model.Producer
import com.ph.syntropyengine.broker.repository.ChannelRepository
import com.ph.syntropyengine.broker.repository.ConsumerRepository
import com.ph.syntropyengine.broker.repository.MessageRepository
import com.ph.syntropyengine.broker.repository.ProducerRepository
import java.io.File
import java.time.OffsetDateTime
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName

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

    protected fun clean() {
        messageRepository.deleteAll()
        consumerRepository.deleteAll()
        producerRepository.deleteAll()
        channelRepository.deleteAll()
    }

    protected fun createChannelAndConsumer(): Pair<Channel, Consumer> {
        val channel = createChannel()
        val consumer = consumerRepository.save(Fixtures.createConsumer(channel.channelId!!))
        return Pair(channel, consumer)
    }

    protected fun createChannel(): Channel {
        val channel = Fixtures.createChannel()
        return channelRepository.save(channel)
    }

    protected fun createChannelWithProducer(): Pair<Channel, Producer> {
        val channel = createChannel()
        val consumer = producerRepository.save(Fixtures.createProducer(channel.channelId!!))
        return Pair(channel, consumer)
    }

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

    protected fun publishMessage(): Message {
        val (channel, producer) = createChannelWithProducer()

        return messageRepository.save(
            Fixtures.createMessage(
                channel.channelId!!,
                producer.producerId!!,
                channel.routingKeys.first()
            )
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
                        "max_wal_senders=1",
                        "-c",
                        "max_replication_slots=2",
                        "-c",
                        "max_logical_replication_workers=1",
                        "-c",
                        "max_worker_processes=2",
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