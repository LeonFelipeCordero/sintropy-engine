package com.ph.sintropyengine.broker.producer.api

import com.ph.sintropyengine.Fixtures
import com.ph.sintropyengine.IntegrationTestBase
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
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
        val request = mapOf("name" to "test-producer")

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/producers")
            .then()
            .statusCode(201)
            .body("name", equalTo("test-producer"))
    }

    @Test
    fun `GET producers by id - should return producer when exists`() {
        val producer = createProducer()

        given()
            .`when`()
            .get("/producers/${producer.name}")
            .then()
            .statusCode(200)
            .body("name", equalTo(producer.name))
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
    fun `GET producers by channel - should return 404 when channel does not exist`() {
        given()
            .`when`()
            .get("/producers/channel/non-existent-channel")
            .then()
            .statusCode(404)
    }

    @Test
    fun `DELETE producers - should delete producer successfully`() {
        val channel = createChannel()
        val producer = createProducer()

        given()
            .`when`()
            .delete("/producers/${producer.name}")
            .then()
            .statusCode(204)

        given()
            .`when`()
            .get("/producers/${producer.name}")
            .then()
            .statusCode(404)
    }

    @Test
    fun `DELETE producers - should return 404 when producer does not exist`() {
        val nonExistentName = UUID.randomUUID().toString()

        given()
            .`when`()
            .delete("/producers/$nonExistentName")
            .then()
            .statusCode(404)
    }

    @Test
    fun `POST messages - should publish message successfully`() {
        val channel = createChannel()
        val producer = createProducer()

        val request =
            mapOf(
                "channelName" to channel.name,
                "producerName" to producer.name,
                "routingKey" to channel.routingKeys.first(),
                "message" to Fixtures.DEFAULT_MESSAGE,
                "headers" to Fixtures.DEFAULT_ATTRIBUTES,
            )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/producers/messages")
            .then()
            .statusCode(201)
            .body("messageId", notNullValue())
            .body("channelName", equalTo(channel.name))
            .body("producerName", equalTo(producer.name))
            .body("routingKey", equalTo(channel.routingKeys.first()))
    }

    @Test
    fun `POST messages - should fail when channel does not exist`() {
        val channel = createChannel()
        val producer = createProducer()

        val request =
            mapOf(
                "channelName" to "non-existent-channel",
                "producerName" to producer.name,
                "routingKey" to "some.key",
                "message" to Fixtures.DEFAULT_MESSAGE,
                "headers" to Fixtures.DEFAULT_ATTRIBUTES,
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

        val request =
            mapOf(
                "channelName" to channel.name,
                "producerName" to "non-existent-producer",
                "routingKey" to channel.routingKeys.first(),
                "message" to Fixtures.DEFAULT_MESSAGE,
                "headers" to Fixtures.DEFAULT_ATTRIBUTES,
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
        val producer = createProducer()

        val request =
            mapOf(
                "channelName" to channel.name,
                "producerName" to producer.name,
                "routingKey" to "invalid.routing.key",
                "message" to Fixtures.DEFAULT_MESSAGE,
                "headers" to Fixtures.DEFAULT_ATTRIBUTES,
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
        val producer = createProducer()

        val request =
            mapOf(
                "channelName" to channel.name,
                "producerName" to producer.name,
                "routingKey" to channel.routingKeys.first(),
                "message" to Fixtures.DEFAULT_MESSAGE,
                "headers" to Fixtures.DEFAULT_ATTRIBUTES,
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

        val request1 = mapOf("name" to "producer-one")
        val request2 = mapOf("name" to "producer-two")

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
    }
}
