package com.ph.sintropyengine.broker.producer.api

import com.ph.sintropyengine.Fixtures
import com.ph.sintropyengine.IntegrationTestBase
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class ProducerApiTest : IntegrationTestBase() {

    @BeforeEach
    fun setUp() {
        clean()
    }

    @Test
    fun `POST producers - should create producer successfully`() {
        val channel = createChannel()

        val request = mapOf(
            "name" to "test-producer",
            "channelName" to channel.name
        )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/producers")
            .then()
            .statusCode(201)
            .body("producerId", notNullValue())
            .body("name", equalTo("test-producer"))
            .body("channelId", equalTo(channel.channelId.toString()))
    }

    @Test
    fun `POST producers - should fail when channel does not exist`() {
        val request = mapOf(
            "name" to "orphan-producer",
            "channelName" to "non-existent-channel"
        )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/producers")
            .then()
            .statusCode(500)
    }

    @Test
    fun `GET producers by id - should return producer when exists`() {
        val channel = createChannel()
        val producer = createProducer(channel)

        given()
            .`when`()
            .get("/producers/${producer.producerId}")
            .then()
            .statusCode(200)
            .body("producerId", equalTo(producer.producerId.toString()))
            .body("name", equalTo(producer.name))
            .body("channelId", equalTo(channel.channelId.toString()))
    }

    @Test
    fun `GET producers by id - should return 404 when producer does not exist`() {
        val nonExistentId = UUID.randomUUID()

        given()
            .`when`()
            .get("/producers/$nonExistentId")
            .then()
            .statusCode(404)
    }

    @Test
    fun `GET producers by channel - should return producers for channel`() {
        val channel = createChannel()
        val producer1 = createProducer(channel)
        val producer2 = createProducer(channel)

        given()
            .`when`()
            .get("/producers/channel/${channel.name}")
            .then()
            .statusCode(200)
            .body("$", hasSize<Any>(2))
    }

    @Test
    fun `GET producers by channel - should return empty list when no producers`() {
        val channel = createChannel()

        given()
            .`when`()
            .get("/producers/channel/${channel.name}")
            .then()
            .statusCode(200)
            .body("$", hasSize<Any>(0))
    }

    @Test
    fun `GET producers by channel - should fail when channel does not exist`() {
        given()
            .`when`()
            .get("/producers/channel/non-existent-channel")
            .then()
            .statusCode(500)
    }

    @Test
    fun `DELETE producers - should delete producer successfully`() {
        val channel = createChannel()
        val producer = createProducer(channel)

        given()
            .`when`()
            .delete("/producers/${producer.producerId}")
            .then()
            .statusCode(204)

        given()
            .`when`()
            .get("/producers/${producer.producerId}")
            .then()
            .statusCode(404)
    }

    @Test
    fun `DELETE producers - should fail when producer does not exist`() {
        val nonExistentId = UUID.randomUUID()

        given()
            .`when`()
            .delete("/producers/$nonExistentId")
            .then()
            .statusCode(500)
    }

    @Test
    fun `POST messages - should publish message successfully`() {
        val channel = createChannel()
        val producer = createProducer(channel)

        val request = mapOf(
            "channelName" to channel.name,
            "producerName" to producer.name,
            "routingKey" to channel.routingKeys.first(),
            "message" to Fixtures.DEFAULT_MESSAGE,
            "headers" to Fixtures.DEFAULT_ATTRIBUTES
        )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/producers/messages")
            .then()
            .statusCode(201)
            .body("messageId", notNullValue())
            .body("channelId", equalTo(channel.channelId.toString()))
            .body("producerId", equalTo(producer.producerId.toString()))
            .body("routingKey", equalTo(channel.routingKeys.first()))
    }

    @Test
    fun `POST messages - should fail when channel does not exist`() {
        val channel = createChannel()
        val producer = createProducer(channel)

        val request = mapOf(
            "channelName" to "non-existent-channel",
            "producerName" to producer.name,
            "routingKey" to "some.key",
            "message" to Fixtures.DEFAULT_MESSAGE,
            "headers" to Fixtures.DEFAULT_ATTRIBUTES
        )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/producers/messages")
            .then()
            .statusCode(500)
    }

    @Test
    fun `POST messages - should fail when producer does not exist`() {
        val channel = createChannel()

        val request = mapOf(
            "channelName" to channel.name,
            "producerName" to "non-existent-producer",
            "routingKey" to channel.routingKeys.first(),
            "message" to Fixtures.DEFAULT_MESSAGE,
            "headers" to Fixtures.DEFAULT_ATTRIBUTES
        )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/producers/messages")
            .then()
            .statusCode(500)
    }

    @Test
    fun `POST messages - should fail when routing key does not exist in channel`() {
        val channel = createChannel()
        val producer = createProducer(channel)

        val request = mapOf(
            "channelName" to channel.name,
            "producerName" to producer.name,
            "routingKey" to "invalid.routing.key",
            "message" to Fixtures.DEFAULT_MESSAGE,
            "headers" to Fixtures.DEFAULT_ATTRIBUTES
        )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/producers/messages")
            .then()
            .statusCode(500)
    }

    @Test
    fun `POST messages - should publish multiple messages to same channel`() {
        val channel = createChannel()
        val producer = createProducer(channel)

        val request = mapOf(
            "channelName" to channel.name,
            "producerName" to producer.name,
            "routingKey" to channel.routingKeys.first(),
            "message" to Fixtures.DEFAULT_MESSAGE,
            "headers" to Fixtures.DEFAULT_ATTRIBUTES
        )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/producers/messages")
            .then()
            .statusCode(201)

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/producers/messages")
            .then()
            .statusCode(201)
    }

    @Test
    fun `POST producers - should create multiple producers for same channel`() {
        val channel = createChannel()

        val request1 = mapOf(
            "name" to "producer-one",
            "channelName" to channel.name
        )

        val request2 = mapOf(
            "name" to "producer-two",
            "channelName" to channel.name
        )

        given()
            .contentType(ContentType.JSON)
            .body(request1)
            .`when`()
            .post("/producers")
            .then()
            .statusCode(201)

        given()
            .contentType(ContentType.JSON)
            .body(request2)
            .`when`()
            .post("/producers")
            .then()
            .statusCode(201)

        given()
            .`when`()
            .get("/producers/channel/${channel.name}")
            .then()
            .statusCode(200)
            .body("$", hasSize<Any>(2))
    }
}
