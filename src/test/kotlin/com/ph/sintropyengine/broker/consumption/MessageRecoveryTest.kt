package com.ph.sintropyengine.broker.consumption

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ph.sintropyengine.Fixtures
import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.channel.model.Channel
import com.ph.sintropyengine.broker.consumption.api.ReadyToStreamResponse
import com.ph.sintropyengine.broker.consumption.api.RecoveryStreamRequest
import com.ph.sintropyengine.broker.consumption.api.StreamingErrorResponse
import com.ph.sintropyengine.broker.consumption.api.response.MessageLogResponse
import com.ph.sintropyengine.broker.producer.model.Producer
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.websockets.next.BasicWebSocketConnector
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@QuarkusTest
class MessageRecoveryTest : IntegrationTestBase() {
    @Inject
    private lateinit var basicWebSocketConnector: BasicWebSocketConnector

    @Inject
    private lateinit var objectMapper: ObjectMapper

    private lateinit var channel: Channel
    private lateinit var producer: Producer
    private lateinit var recoveryUri: URI

    @BeforeEach
    fun setUp() {
        clean()

        channel = createChannel()
        producer = createProducer()
        recoveryUri = URI.create("ws://localhost:8081/ws/recovery/${channel.name}/${Fixtures.DEFAULT_ROUTING_KEY}")
    }

    @Test
    fun `should connect and receive ready to stream response`() =
        runTest {
            val latch = CountDownLatch(1)
            var readyResponse: ReadyToStreamResponse? = null

            basicWebSocketConnector
                .baseUri(recoveryUri)
                .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                .onTextMessage { _, message ->
                    readyResponse = objectMapper.readValue(message, ReadyToStreamResponse::class.java)
                    latch.countDown()
                }.connectAndAwait()

            latch.await(5, TimeUnit.SECONDS)

            assertThat(readyResponse).isNotNull
            assertThat(readyResponse!!.channelName).isEqualTo(channel.name)
            assertThat(readyResponse.routingKey).isEqualTo(Fixtures.DEFAULT_ROUTING_KEY)
            assertThat(readyResponse.connectionId).isNotEmpty()
            assertThat(readyResponse.message).contains("Ready to stream")
        }

    @Test
    fun `should stream all messages when streamAll is true`() =
        runTest {
            val message1 = publishMessage(channel, producer)
            val message2 = publishMessage(channel, producer)
            val message3 = publishMessage(channel, producer)

            val receivedMessages = mutableListOf<MessageLogResponse>()
            val latch = CountDownLatch(3)
            var streamingComplete = false

            val connection =
                basicWebSocketConnector
                    .baseUri(recoveryUri)
                    .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                    .onTextMessage { _, message ->
                        when {
                            message.contains("Ready to stream") -> {
                                // Ready message received, ignore
                            }

                            message.contains("Streaming complete") -> {
                                streamingComplete = true
                            }

                            message.startsWith("[") -> {
                                val messages: List<MessageLogResponse> = objectMapper.readValue(message)
                                receivedMessages.addAll(messages)
                                messages.forEach { _ -> latch.countDown() }
                            }
                        }
                    }.connectAndAwait()

            Thread.sleep(500)

            val request =
                RecoveryStreamRequest(
                    batchSize = 10,
                    delayInMs = 50,
                    streamAll = true,
                )
            connection.sendTextAndAwait(objectMapper.writeValueAsString(request))

            latch.await(10, TimeUnit.SECONDS)
            Thread.sleep(500)

            assertThat(receivedMessages).hasSize(3)
            assertThat(receivedMessages.map { it.messageId }).containsExactlyInAnyOrder(
                message1.messageId,
                message2.messageId,
                message3.messageId,
            )
            assertThat(streamingComplete).isTrue()
        }

    @Test
    fun `should stream messages within time range when from and to are provided`() =
        runTest {
            val message1 = publishMessage(channel, producer)
            Thread.sleep(100)
            val message2 = publishMessage(channel, producer)
            Thread.sleep(100)
            val message3 = publishMessage(channel, producer)

            val receivedMessages = mutableListOf<MessageLogResponse>()
            val completeLatch = CountDownLatch(1)

            val connection =
                basicWebSocketConnector
                    .baseUri(recoveryUri)
                    .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                    .onTextMessage { _, message ->
                        when {
                            message.contains("Ready to stream") -> {}

                            message.contains("Streaming complete") -> {
                                completeLatch.countDown()
                            }

                            message.startsWith("[") -> {
                                val messages: List<MessageLogResponse> = objectMapper.readValue(message)
                                receivedMessages.addAll(messages)
                            }
                        }
                    }.connectAndAwait()

            Thread.sleep(500)

            val request =
                RecoveryStreamRequest(
                    batchSize = 10,
                    delayInMs = 50,
                    from = message1.timestamp!!.minusSeconds(1),
                    to = message2.timestamp!!.plusNanos(1000),
                    streamAll = false,
                )
            connection.sendTextAndAwait(objectMapper.writeValueAsString(request))

            completeLatch.await(10, TimeUnit.SECONDS)

            assertThat(receivedMessages).hasSize(2)
            assertThat(receivedMessages.map { it.messageId }).containsExactlyInAnyOrder(
                message1.messageId,
                message2.messageId,
            )
        }

    @Test
    fun `should stream messages from a given time without end time`() =
        runTest {
            val message1 = publishMessage(channel, producer)
            Thread.sleep(50)
            val message2 = publishMessage(channel, producer)
            Thread.sleep(50)
            val message3 = publishMessage(channel, producer)

            val receivedMessages = mutableListOf<MessageLogResponse>()
            val completeLatch = CountDownLatch(1)

            val connection =
                basicWebSocketConnector
                    .baseUri(recoveryUri)
                    .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                    .onTextMessage { _, message ->
                        when {
                            message.contains("Ready to stream") -> {}

                            message.contains("Streaming complete") -> {
                                completeLatch.countDown()
                            }

                            message.startsWith("[") -> {
                                val messages: List<MessageLogResponse> = objectMapper.readValue(message)
                                receivedMessages.addAll(messages)
                            }
                        }
                    }.connectAndAwait()

            Thread.sleep(500)

            val request =
                RecoveryStreamRequest(
                    batchSize = 10,
                    delayInMs = 50,
                    from = message1.timestamp!!.minusSeconds(1),
                    to = null,
                    streamAll = false,
                )
            connection.sendTextAndAwait(objectMapper.writeValueAsString(request))

            completeLatch.await(10, TimeUnit.SECONDS)

            assertThat(receivedMessages).hasSize(3)
            assertThat(receivedMessages.map { it.messageId }).containsExactlyInAnyOrder(
                message1.messageId,
                message2.messageId,
                message3.messageId,
            )
        }

    @Test
    fun `should return empty when no messages match the time range`() =
        runTest {
            publishMessage(channel, producer)

            val receivedMessages = mutableListOf<MessageLogResponse>()
            val completeLatch = CountDownLatch(1)

            val connection =
                basicWebSocketConnector
                    .baseUri(recoveryUri)
                    .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                    .onTextMessage { _, message ->
                        when {
                            message.contains("Ready to stream") -> {}

                            message.contains("Streaming complete") -> {
                                completeLatch.countDown()
                            }

                            message.startsWith("[") -> {
                                val messages: List<MessageLogResponse> = objectMapper.readValue(message)
                                receivedMessages.addAll(messages)
                            }
                        }
                    }.connectAndAwait()

            Thread.sleep(500)

            val request =
                RecoveryStreamRequest(
                    batchSize = 10,
                    delayInMs = 50,
                    from = OffsetDateTime.now().plusHours(1),
                    to = null,
                    streamAll = false,
                )
            connection.sendTextAndAwait(objectMapper.writeValueAsString(request))

            completeLatch.await(10, TimeUnit.SECONDS)

            assertThat(receivedMessages).isEmpty()
        }

    @Test
    fun `should return error when channel does not exist`() =
        runTest {
            val nonExistentUri =
                URI.create("ws://localhost:8081/ws/recovery/non-existent-channel/${Fixtures.DEFAULT_ROUTING_KEY}")
            var errorResponse: StreamingErrorResponse? = null
            val latch = CountDownLatch(1)

            val connection =
                basicWebSocketConnector
                    .baseUri(nonExistentUri)
                    .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                    .onTextMessage { _, message ->
                        when {
                            message.contains("Ready to stream") -> {}

                            message.contains("error") -> {
                                errorResponse = objectMapper.readValue(message, StreamingErrorResponse::class.java)
                                latch.countDown()
                            }
                        }
                    }.connectAndAwait()

            Thread.sleep(500)

            val request = RecoveryStreamRequest(streamAll = true)
            connection.sendTextAndAwait(objectMapper.writeValueAsString(request))

            latch.await(5, TimeUnit.SECONDS)

            assertThat(errorResponse).isNotNull
            assertThat(errorResponse!!.error)
                .contains("Channel with name non-existent-channel and routing key test.1 not found")
        }

    @Test
    fun `should return error when routing key does not exist`() =
        runTest {
            val invalidRoutingKeyUri = URI.create("ws://localhost:8081/ws/recovery/${channel.name}/invalid.routing.key")
            var errorResponse: StreamingErrorResponse? = null
            val latch = CountDownLatch(1)

            val connection =
                basicWebSocketConnector
                    .baseUri(invalidRoutingKeyUri)
                    .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                    .onTextMessage { _, message ->
                        when {
                            message.contains("Ready to stream") -> {}

                            message.contains("error") -> {
                                errorResponse = objectMapper.readValue(message, StreamingErrorResponse::class.java)
                                latch.countDown()
                            }
                        }
                    }.connectAndAwait()

            Thread.sleep(500)

            val request = RecoveryStreamRequest(streamAll = true)
            connection.sendTextAndAwait(objectMapper.writeValueAsString(request))

            latch.await(5, TimeUnit.SECONDS)

            assertThat(errorResponse).isNotNull
            assertThat(errorResponse!!.error).contains("Channel with name ${channel.name} and routing key invalid.routing.key not found")
        }

    @Test
    fun `should return error when from is missing and streamAll is false`() =
        runTest {
            var errorResponse: StreamingErrorResponse? = null
            val latch = CountDownLatch(1)

            val connection =
                basicWebSocketConnector
                    .baseUri(recoveryUri)
                    .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                    .onTextMessage { _, message ->
                        when {
                            message.contains("Ready to stream") -> {}

                            message.contains("error") -> {
                                errorResponse = objectMapper.readValue(message, StreamingErrorResponse::class.java)
                                latch.countDown()
                            }
                        }
                    }.connectAndAwait()

            Thread.sleep(500)

            val request =
                RecoveryStreamRequest(
                    streamAll = false,
                    from = null,
                )
            connection.sendTextAndAwait(objectMapper.writeValueAsString(request))

            latch.await(5, TimeUnit.SECONDS)

            assertThat(errorResponse).isNotNull
            assertThat(errorResponse!!.error).contains("Provide an start point when not requesting a full recovery")
        }

    @Test
    fun `should stream messages in batches`() =
        runTest {
            repeat(15) {
                publishMessage(channel, producer)
            }

            val receivedBatches = mutableListOf<List<MessageLogResponse>>()
            val completeLatch = CountDownLatch(1)

            val connection =
                basicWebSocketConnector
                    .baseUri(recoveryUri)
                    .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                    .onTextMessage { _, message ->
                        when {
                            message.contains("Ready to stream") -> {}

                            message.contains("Streaming complete") -> {
                                completeLatch.countDown()
                            }

                            message.startsWith("[") -> {
                                val messages: List<MessageLogResponse> = objectMapper.readValue(message)
                                if (messages.isNotEmpty()) {
                                    receivedBatches.add(messages)
                                }
                            }
                        }
                    }.connectAndAwait()

            Thread.sleep(500)

            val request =
                RecoveryStreamRequest(
                    batchSize = 5,
                    delayInMs = 50,
                    streamAll = true,
                )
            connection.sendTextAndAwait(objectMapper.writeValueAsString(request))

            completeLatch.await(10, TimeUnit.SECONDS)

            assertThat(receivedBatches).hasSize(3)
            assertThat(receivedBatches[0]).hasSize(5)
            assertThat(receivedBatches[1]).hasSize(5)
            assertThat(receivedBatches[2]).hasSize(5)

            val allMessages = receivedBatches.flatten()
            assertThat(allMessages).hasSize(15)
        }

    @Test
    fun `should only stream messages for the specified routing key`() =
        runTest {
            channelRepository.addRoutingKey(channel.channelId!!, "other.routing.key")

            val message1 = publishMessage(channel, producer, Fixtures.DEFAULT_ROUTING_KEY)
            val message2 = publishMessage(channel, producer, "other.routing.key")
            val message3 = publishMessage(channel, producer, Fixtures.DEFAULT_ROUTING_KEY)

            val receivedMessages = mutableListOf<MessageLogResponse>()
            val completeLatch = CountDownLatch(1)

            val connection =
                basicWebSocketConnector
                    .baseUri(recoveryUri)
                    .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                    .onTextMessage { _, message ->
                        when {
                            message.contains("Ready to stream") -> {}

                            message.contains("Streaming complete") -> {
                                completeLatch.countDown()
                            }

                            message.startsWith("[") -> {
                                val messages: List<MessageLogResponse> = objectMapper.readValue(message)
                                receivedMessages.addAll(messages)
                            }
                        }
                    }.connectAndAwait()

            Thread.sleep(500)

            val request =
                RecoveryStreamRequest(
                    batchSize = 10,
                    delayInMs = 50,
                    streamAll = true,
                )
            connection.sendTextAndAwait(objectMapper.writeValueAsString(request))

            completeLatch.await(10, TimeUnit.SECONDS)

            assertThat(receivedMessages).hasSize(2)
            assertThat(receivedMessages.map { it.messageId }).containsExactlyInAnyOrder(
                message1.messageId,
                message3.messageId,
            )
            assertThat(receivedMessages.map { it.messageId }).doesNotContain(message2.messageId)
        }

    @Test
    fun `should close connection properly`() =
        runTest {
            val openLatch = CountDownLatch(1)
            val closeLatch = CountDownLatch(1)

            val connection =
                basicWebSocketConnector
                    .baseUri(recoveryUri)
                    .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                    .onTextMessage { _, message ->
                        if (message.contains("Ready to stream")) {
                            openLatch.countDown()
                        }
                    }.onClose { _, _ ->
                        closeLatch.countDown()
                    }.connectAndAwait()

            openLatch.await(5, TimeUnit.SECONDS)

            connection.closeAndAwait()

            val closed = closeLatch.await(5, TimeUnit.SECONDS)
            assertThat(closed).isTrue()
        }

    @Test
    fun `REST - should retrigger message successfully`() {
        val message = publishMessage(channel, producer)

        given()
            .contentType(ContentType.JSON)
            .`when`()
            .post("/recovery/messages/${message.messageId}/retrigger")
            .then()
            .statusCode(200)
            .body("messageId", equalTo(message.messageId.toString()))
            .body("channelName", equalTo(channel.name))
            .body("producerName", equalTo(producer.name))
            .body("routingKey", equalTo(message.routingKey))
            .body("message", notNullValue())
    }

    @Test
    fun `REST - should return 500 when message does not exist`() {
        val nonExistentId = UUID.randomUUID()

        given()
            .contentType(ContentType.JSON)
            .`when`()
            .post("/recovery/messages/$nonExistentId/retrigger")
            .then()
            .statusCode(500)
    }
}
