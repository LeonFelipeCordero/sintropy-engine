package com.ph.sintropyengine.broker.consumption.api

import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.consumption.service.PollingFifoQueue
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@QuarkusTest
class CircuitBreakerApiTest : IntegrationTestBase() {
    @Inject
    private lateinit var pollingFifoQueue: PollingFifoQueue

    @BeforeEach
    fun setUp() {
        clean()
    }

    @Nested
    inner class ListOpenCircuits {
        @Test
        fun `GET open - should return empty list when no open circuits`() {
            given()
                .`when`()
                .get("/circuit-breakers/open")
                .then()
                .statusCode(200)
                .body("$", empty<Any>())
        }

        @Test
        fun `GET open - should return all open circuits`() {
            val channel1 = createFifoQueueChannel()
            val producer1 = createProducer()
            val message1 = publishMessage(channel1, producer1)

            val channel2 = createFifoQueueChannel()
            val producer2 = createProducer()
            val message2 = publishMessage(channel2, producer2)

            pollingFifoQueue.poll(channel1.channelId!!, channel1.routingKeys.first())
            pollingFifoQueue.markAsFailed(message1.messageId)

            pollingFifoQueue.poll(channel2.channelId!!, channel2.routingKeys.first())
            pollingFifoQueue.markAsFailed(message2.messageId)

            given()
                .`when`()
                .get("/circuit-breakers/open")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(2))
        }
    }

    @Nested
    inner class ListCircuitsForChannel {
        @Test
        fun `GET channels - should return circuits in CLOSED state when channel exists`() {
            val channel = createFifoQueueChannel()

            given()
                .`when`()
                .get("/circuit-breakers/channels/${channel.name}")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(1))
                .body("[0].channelName", equalTo(channel.name))
                .body("[0].state", equalTo("CLOSED"))
        }

        @Test
        fun `GET channels - should return circuits for channel`() {
            val channel = createFifoQueueChannel()
            val producer = createProducer()
            val message = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message.messageId)

            given()
                .`when`()
                .get("/circuit-breakers/channels/${channel.name}")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(1))
                .body("[0].channelName", equalTo(channel.name))
                .body("[0].state", equalTo("OPEN"))
        }

        @Test
        fun `GET channels - should fail when channel not found`() {
            given()
                .`when`()
                .get("/circuit-breakers/channels/non-existent")
                .then()
                .statusCode(500)
        }
    }

    @Nested
    inner class GetCircuitBreaker {
        @Test
        fun `GET circuit breaker - should return circuit with CLOSED state when channel exists`() {
            val channel = createFifoQueueChannel()

            given()
                .`when`()
                .get("/circuit-breakers/channels/${channel.name}/routing-keys/${channel.routingKeys.first()}")
                .then()
                .statusCode(200)
                .body("channelName", equalTo(channel.name))
                .body("routingKey", equalTo(channel.routingKeys.first()))
                .body("state", equalTo("CLOSED"))
        }

        @Test
        fun `GET circuit breaker - should return circuit when exists`() {
            val channel = createFifoQueueChannel()
            val producer = createProducer()
            val message = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message.messageId)

            given()
                .`when`()
                .get("/circuit-breakers/channels/${channel.name}/routing-keys/${channel.routingKeys.first()}")
                .then()
                .statusCode(200)
                .body("channelName", equalTo(channel.name))
                .body("routingKey", equalTo(channel.routingKeys.first()))
                .body("state", equalTo("OPEN"))
                .body("failedMessageId", equalTo(message.messageId.toString()))
                .body("openedAt", notNullValue())
        }

        @Test
        fun `GET circuit breaker - should fail when channel not found`() {
            given()
                .`when`()
                .get("/circuit-breakers/channels/non-existent/routing-keys/key")
                .then()
                .statusCode(500)
        }

        @Test
        fun `GET circuit breaker - should fail when routing key not in channel`() {
            val channel = createFifoQueueChannel()

            given()
                .`when`()
                .get("/circuit-breakers/channels/${channel.name}/routing-keys/invalid-key")
                .then()
                .statusCode(500)
        }
    }

    @Nested
    inner class GetCircuitState {
        @Test
        fun `GET state - should return CLOSED when no circuit exists`() {
            val channel = createFifoQueueChannel()

            given()
                .`when`()
                .get("/circuit-breakers/channels/${channel.name}/routing-keys/${channel.routingKeys.first()}/state")
                .then()
                .statusCode(200)
                .body("state", equalTo("CLOSED"))
        }

        @Test
        fun `GET state - should return OPEN when circuit is open`() {
            val channel = createFifoQueueChannel()
            val producer = createProducer()
            val message = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message.messageId)

            given()
                .`when`()
                .get("/circuit-breakers/channels/${channel.name}/routing-keys/${channel.routingKeys.first()}/state")
                .then()
                .statusCode(200)
                .body("state", equalTo("OPEN"))
        }

        @Test
        fun `GET state - should fail when channel not found`() {
            given()
                .`when`()
                .get("/circuit-breakers/channels/non-existent/routing-keys/key/state")
                .then()
                .statusCode(500)
        }

        @Test
        fun `GET state - should fail when routing key not in channel`() {
            val channel = createFifoQueueChannel()

            given()
                .`when`()
                .get("/circuit-breakers/channels/${channel.name}/routing-keys/invalid-key/state")
                .then()
                .statusCode(500)
        }
    }

    @Nested
    inner class CloseCircuit {
        @Test
        fun `POST close - should close circuit without recovery`() {
            val channel = createFifoQueueChannel()
            val producer = createProducer()
            val message = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message.messageId)

            given()
                .contentType(ContentType.JSON)
                .`when`()
                .post("/circuit-breakers/channels/${channel.name}/routing-keys/${channel.routingKeys.first()}/close")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("recoveredCount", equalTo(0))

            given()
                .`when`()
                .get("/circuit-breakers/channels/${channel.name}/routing-keys/${channel.routingKeys.first()}/state")
                .then()
                .statusCode(200)
                .body("state", equalTo("CLOSED"))
        }

        @Test
        fun `POST close - should close circuit with recovery`() {
            val channel = createFifoQueueChannel()
            val producer = createProducer()
            val message1 = publishMessage(channel, producer)
            val message2 = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message1.messageId)

            given()
                .contentType(ContentType.JSON)
                .queryParam("recover", true)
                .`when`()
                .post("/circuit-breakers/channels/${channel.name}/routing-keys/${channel.routingKeys.first()}/close")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("recoveredCount", equalTo(2))

            given()
                .`when`()
                .get("/circuit-breakers/channels/${channel.name}/routing-keys/${channel.routingKeys.first()}/state")
                .then()
                .statusCode(200)
                .body("state", equalTo("CLOSED"))
        }

        @Test
        fun `POST close - should return success when circuit already closed`() {
            val channel = createFifoQueueChannel()

            given()
                .contentType(ContentType.JSON)
                .`when`()
                .post("/circuit-breakers/channels/${channel.name}/routing-keys/${channel.routingKeys.first()}/close")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("recoveredCount", equalTo(0))
        }

        @Test
        fun `POST close - should fail when channel not found`() {
            given()
                .contentType(ContentType.JSON)
                .`when`()
                .post("/circuit-breakers/channels/non-existent/routing-keys/key/close")
                .then()
                .statusCode(500)
        }

        @Test
        fun `POST close - should fail when routing key not in channel`() {
            val channel = createFifoQueueChannel()

            given()
                .contentType(ContentType.JSON)
                .`when`()
                .post("/circuit-breakers/channels/${channel.name}/routing-keys/invalid-key/close")
                .then()
                .statusCode(500)
        }

        @Test
        fun `POST close with recover - should return 0 when circuit already closed`() {
            val channel = createFifoQueueChannel()

            given()
                .contentType(ContentType.JSON)
                .queryParam("recover", true)
                .`when`()
                .post("/circuit-breakers/channels/${channel.name}/routing-keys/${channel.routingKeys.first()}/close")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("recoveredCount", equalTo(0))
        }
    }
}
