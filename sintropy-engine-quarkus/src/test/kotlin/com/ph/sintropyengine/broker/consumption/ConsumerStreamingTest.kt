package com.ph.sintropyengine.broker.consumption

import com.fasterxml.jackson.databind.ObjectMapper
import com.ph.sintropyengine.Fixtures
import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.TestWithFullReplicationProfile
import com.ph.sintropyengine.broker.channel.model.Channel
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.service.ConnectionRouter
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.quarkus.websockets.next.BasicWebSocketConnector
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@QuarkusTest
@TestProfile(TestWithFullReplicationProfile::class)
class ConsumerStreamingTest : IntegrationTestBase() {
    @Inject
    private lateinit var connectionRouter: ConnectionRouter

    @Inject
    private lateinit var basicWebSocketConnector: BasicWebSocketConnector

    @Inject
    private lateinit var objectMapper: ObjectMapper

    private lateinit var channel: Channel
    private lateinit var streamingUri: URI

    @BeforeEach
    fun setUp() {
        clean()

        channel = createChannel(channelType = ChannelType.STREAM)
        streamingUri = URI.create("ws://localhost:8081/ws/streaming/${channel.name}/${Fixtures.DEFAULT_ROUTING_KEY}")
    }

    @Test
    fun `should establish websocket connection and register the connection in the connection router`() =
        runTest {
            val latch = CountDownLatch(1)

            basicWebSocketConnector
                .baseUri(streamingUri)
                .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                .onTextMessage { _, _ ->
                    latch.countDown()
                }.connectAndAwait()

            latch.await(1, TimeUnit.SECONDS)

            val connections = connectionRouter.getByRoutingKey(channel.routing(Fixtures.DEFAULT_ROUTING_KEY))

            Assertions.assertThat(connections).hasSize(1)
        }

    @Test
    fun `should close connection and remove connection from the connection router`() =
        runTest {
            val latch = CountDownLatch(1)
            val closeLatch = CountDownLatch(1)

            val connection =
                basicWebSocketConnector
                    .baseUri(streamingUri)
                    .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                    .onTextMessage { _, _ ->
                        latch.countDown()
                    }.onClose { _, _ ->
                        closeLatch.countDown()
                    }.connectAndAwait()

            latch.await(1, TimeUnit.SECONDS)

            connection.closeAndAwait()
            closeLatch.await(1, TimeUnit.SECONDS)

            val connections = connectionRouter.getByRoutingKey(channel.routing(Fixtures.DEFAULT_ROUTING_KEY))

            Assertions.assertThat(connections).hasSize(0)
        }

    @Test
    fun `should receive notifications on every message`() =
        runTest {
            val latch = CountDownLatch(5)
            val producer = createProducer(channel)

            val receivedMessages = mutableListOf<Message>()
            val sentMessages = mutableListOf<Message>()
            basicWebSocketConnector
                .baseUri(streamingUri)
                .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                .onTextMessage { _, message ->
                    receivedMessages.add(objectMapper.readValue(message, Message::class.java))
                    latch.countDown()
                }.connectAndAwait()

            sentMessages.add(
                producerService.publishMessage(Fixtures.createMessageRequest(channel.name, producer.name)),
            )
            sentMessages.add(
                producerService.publishMessage(Fixtures.createMessageRequest(channel.name, producer.name)),
            )
            sentMessages.add(
                producerService.publishMessage(Fixtures.createMessageRequest(channel.name, producer.name)),
            )
            sentMessages.add(
                producerService.publishMessage(Fixtures.createMessageRequest(channel.name, producer.name)),
            )
            sentMessages.add(
                producerService.publishMessage(Fixtures.createMessageRequest(channel.name, producer.name)),
            )

            latch.await()

            Assertions.assertThat(receivedMessages).hasSize(5)
            Assertions.assertThat(sentMessages).usingRecursiveComparison().isEqualTo(receivedMessages)
        }

    @Test
    fun `should get a message and it should not be in message table and should be processed in message log`() =
        runTest {
            val latch = CountDownLatch(5)
            val producer = createProducer(channel)

            val receivedMessages = mutableListOf<Message>()
            val sentMessages = mutableListOf<Message>()
            basicWebSocketConnector
                .baseUri(streamingUri)
                .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                .onTextMessage { _, message ->
                    receivedMessages.add(objectMapper.readValue(message, Message::class.java))
                    latch.countDown()
                }.connectAndAwait()

            sentMessages.add(
                producerService.publishMessage(Fixtures.createMessageRequest(channel.name, producer.name)),
            )
            sentMessages.add(
                producerService.publishMessage(Fixtures.createMessageRequest(channel.name, producer.name)),
            )
            sentMessages.add(
                producerService.publishMessage(Fixtures.createMessageRequest(channel.name, producer.name)),
            )
            sentMessages.add(
                producerService.publishMessage(Fixtures.createMessageRequest(channel.name, producer.name)),
            )
            sentMessages.add(
                producerService.publishMessage(Fixtures.createMessageRequest(channel.name, producer.name)),
            )

            latch.await()

            val wait =
                launch {
                    repeat(5) {
                        val messages = messageRepository.findAll()
                        if (messages.isNotEmpty()) {
                            delay(1000)
                        } else {
                            return@repeat
                        }
                    }
                }
            wait.join()

            val messages = messageRepository.findAll()
            Assertions.assertThat(messages).hasSize(0)

            val messageLogs = messageRepository.findAllMessageLog()
            Assertions.assertThat(messageLogs).hasSize(5)
            Assertions.assertThat(messageLogs.map { it.processed }.toSet().first()).isTrue
        }
}
