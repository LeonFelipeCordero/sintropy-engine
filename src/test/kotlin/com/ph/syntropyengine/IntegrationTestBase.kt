package com.ph.syntropyengine

import com.ph.syntropyengine.broker.model.Channel
import com.ph.syntropyengine.broker.model.Consumer
import com.ph.syntropyengine.broker.model.Producer
import com.ph.syntropyengine.broker.repository.ChannelRepository
import com.ph.syntropyengine.broker.repository.ConsumerRepository
import com.ph.syntropyengine.broker.repository.MessageRepository
import com.ph.syntropyengine.broker.repository.ProducerRepository
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    companion object {
        const val DB_USER = "postgres"
        const val DB_PASSWORD = "postgres"
        const val DB_NAME = "postgres"

        val timescaleImage: DockerImageName = DockerImageName
            .parse("timescale/timescaledb-ha:pg17")
            .asCompatibleSubstituteFor("postgres");

        @Container
        val container: PostgreSQLContainer<*> =
            PostgreSQLContainer(timescaleImage)
                .withUsername(DB_USER)
                .withPassword(DB_PASSWORD)
                .withDatabaseName(DB_NAME)
                .withReuse(true)
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