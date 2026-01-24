package com.ph.sintropyengine.broker.consumption.api

import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.consumption.service.PollingStandardQueue
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class QueueApiTest : IntegrationTestBase() {
    @Inject
    private lateinit var pollingStandardQueue: PollingStandardQueue

    @BeforeEach
    fun setUp() {
        clean()
    }

    @Nested
    inner class BulkMarkAsFailed {
        @Test
        fun `POST bulk failed - should mark multiple messages as failed`() {
            val (channel, producer) = createChannelWithProducer()
            val message1 = publishMessage(channel, producer)
            val message2 = publishMessage(channel, producer)
            val message3 = publishMessage(channel, producer)

            pollingStandardQueue.poll(channel.channelId!!, channel.routingKeys.first(), 3)

            given()
                .contentType(ContentType.JSON)
                .body(mapOf("messageIds" to listOf(message1.messageId, message2.messageId, message3.messageId)))
                .`when`()
                .post("/queues/messages/failed/bulk")
                .then()
                .statusCode(200)
                .body("processed", hasSize<Any>(3))
                .body(
                    "processed",
                    containsInAnyOrder(
                        message1.messageId.toString(),
                        message2.messageId.toString(),
                        message3.messageId.toString(),
                    ),
                )
        }

        @Test
        fun `POST bulk failed - should return only existing IDs when some messages do not exist`() {
            val (channel, producer) = createChannelWithProducer()
            val message1 = publishMessage(channel, producer)
            val nonExistentId = UUID.randomUUID()

            pollingStandardQueue.poll(channel.channelId!!, channel.routingKeys.first(), 1)

            given()
                .contentType(ContentType.JSON)
                .body(mapOf("messageIds" to listOf(message1.messageId, nonExistentId)))
                .`when`()
                .post("/queues/messages/failed/bulk")
                .then()
                .statusCode(200)
                .body("processed", hasSize<Any>(1))
                .body("processed", containsInAnyOrder(message1.messageId.toString()))
        }

        @Test
        fun `POST bulk failed - should return empty when no messages exist`() {
            val nonExistentId1 = UUID.randomUUID()
            val nonExistentId2 = UUID.randomUUID()

            given()
                .contentType(ContentType.JSON)
                .body(mapOf("messageIds" to listOf(nonExistentId1, nonExistentId2)))
                .`when`()
                .post("/queues/messages/failed/bulk")
                .then()
                .statusCode(200)
                .body("processed", empty<Any>())
        }

        @Test
        fun `POST bulk failed - should return bad request when message IDs list is empty`() {
            given()
                .contentType(ContentType.JSON)
                .body(mapOf("messageIds" to emptyList<UUID>()))
                .`when`()
                .post("/queues/messages/failed/bulk")
                .then()
                .statusCode(400)
        }
    }

    @Nested
    inner class BulkDequeue {
        @Test
        fun `POST bulk dequeue - should dequeue multiple messages`() {
            val (channel, producer) = createChannelWithProducer()
            val message1 = publishMessage(channel, producer)
            val message2 = publishMessage(channel, producer)
            val message3 = publishMessage(channel, producer)

            pollingStandardQueue.poll(channel.channelId!!, channel.routingKeys.first(), 3)

            given()
                .contentType(ContentType.JSON)
                .body(mapOf("messageIds" to listOf(message1.messageId, message2.messageId, message3.messageId)))
                .`when`()
                .post("/queues/messages/dequeue/bulk")
                .then()
                .statusCode(200)
                .body("processed", hasSize<Any>(3))
                .body(
                    "processed",
                    containsInAnyOrder(
                        message1.messageId.toString(),
                        message2.messageId.toString(),
                        message3.messageId.toString(),
                    ),
                )
        }

        @Test
        fun `POST bulk dequeue - should return only existing IDs when some messages do not exist`() {
            val (channel, producer) = createChannelWithProducer()
            val message1 = publishMessage(channel, producer)
            val nonExistentId = UUID.randomUUID()

            pollingStandardQueue.poll(channel.channelId!!, channel.routingKeys.first(), 1)

            given()
                .contentType(ContentType.JSON)
                .body(mapOf("messageIds" to listOf(message1.messageId, nonExistentId)))
                .`when`()
                .post("/queues/messages/dequeue/bulk")
                .then()
                .statusCode(200)
                .body("processed", hasSize<Any>(1))
                .body("processed", containsInAnyOrder(message1.messageId.toString()))
        }

        @Test
        fun `POST bulk dequeue - should fail when some messages are still in READY status`() {
            val (channel, producer) = createChannelWithProducer()
            val message1 = publishMessage(channel, producer)
            val message2 = publishMessage(channel, producer)

            pollingStandardQueue.poll(channel.channelId!!, channel.routingKeys.first(), 1)

            given()
                .contentType(ContentType.JSON)
                .body(mapOf("messageIds" to listOf(message1.messageId, message2.messageId)))
                .`when`()
                .post("/queues/messages/dequeue/bulk")
                .then()
                .statusCode(500)
        }

        @Test
        fun `POST bulk dequeue - should return empty when no messages exist`() {
            val nonExistentId1 = UUID.randomUUID()
            val nonExistentId2 = UUID.randomUUID()

            given()
                .contentType(ContentType.JSON)
                .body(mapOf("messageIds" to listOf(nonExistentId1, nonExistentId2)))
                .`when`()
                .post("/queues/messages/dequeue/bulk")
                .then()
                .statusCode(200)
                .body("processed", empty<Any>())
        }

        @Test
        fun `POST bulk dequeue - should return bad request when message IDs list is empty`() {
            given()
                .contentType(ContentType.JSON)
                .body(mapOf("messageIds" to emptyList<UUID>()))
                .`when`()
                .post("/queues/messages/dequeue/bulk")
                .then()
                .statusCode(400)
        }
    }
}
