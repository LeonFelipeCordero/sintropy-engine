package com.ph.sintropyengine.broker.channel.api

import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.chennel.model.ChannelType
import com.ph.sintropyengine.broker.chennel.model.ConsumptionType
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class ChannelApiTest : IntegrationTestBase() {
    @BeforeEach
    fun setUp() {
        clean()
    }

    @Test
    fun `POST channels - should create channel successfully`() {
        val request =
            mapOf(
                "name" to "test-channel",
                "channelType" to ChannelType.QUEUE.name,
                "routingKeys" to listOf("test.routing.key"),
                "consumptionType" to ConsumptionType.STANDARD.name,
            )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/channels")
            .then()
            .statusCode(201)
            .body("channelId", notNullValue())
            .body("name", equalTo("test-channel"))
            .body("channelType", equalTo("QUEUE"))
            .body("routingKeys", hasSize<String>(1))
            .body("routingKeys", hasItem("test.routing.key"))
            .body("consumptionType", equalTo("STANDARD"))
    }

    @Test
    fun `POST channels - should create stream channel without consumption type`() {
        val request =
            mapOf(
                "name" to "test-stream",
                "channelType" to ChannelType.STREAM.name,
                "routingKeys" to listOf("stream.key"),
            )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/channels")
            .then()
            .statusCode(201)
            .body("channelId", notNullValue())
            .body("name", equalTo("test-stream"))
            .body("channelType", equalTo("STREAM"))
            .body("consumptionType", equalTo(null))
    }

    @Test
    fun `POST channels - should fail when channel name already exists`() {
        val request =
            mapOf(
                "name" to "duplicate-channel",
                "channelType" to ChannelType.QUEUE.name,
                "routingKeys" to listOf("test.key"),
                "consumptionType" to ConsumptionType.STANDARD.name,
            )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/channels")
            .then()
            .statusCode(201)

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/channels")
            .then()
            .statusCode(500)
    }

    @Test
    fun `POST channels - should fail when routing keys are empty`() {
        val request =
            mapOf(
                "name" to "no-routing-keys-channel",
                "channelType" to ChannelType.QUEUE.name,
                "routingKeys" to emptyList<String>(),
                "consumptionType" to ConsumptionType.STANDARD.name,
            )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/channels")
            .then()
            .statusCode(500)
    }

    @Test
    fun `GET channels by id - should return channel when exists`() {
        val channel = createChannel()

        given()
            .`when`()
            .get("/channels/${channel.channelId}")
            .then()
            .statusCode(200)
            .body("channelId", equalTo(channel.channelId.toString()))
            .body("name", equalTo(channel.name))
            .body("channelType", equalTo(channel.channelType.name))
    }

    @Test
    fun `GET channels by id - should return 404 when channel does not exist`() {
        val nonExistentId = UUID.randomUUID()

        given()
            .`when`()
            .get("/channels/$nonExistentId")
            .then()
            .statusCode(404)
    }

    @Test
    fun `GET channels by name - should return channel when exists`() {
        val channel = createChannel()

        given()
            .`when`()
            .get("/channels/name/${channel.name}")
            .then()
            .statusCode(200)
            .body("channelId", equalTo(channel.channelId.toString()))
            .body("name", equalTo(channel.name))
    }

    @Test
    fun `GET channels by name - should return 404 when channel does not exist`() {
        given()
            .`when`()
            .get("/channels/name/non-existent-channel")
            .then()
            .statusCode(404)
    }

    @Test
    fun `DELETE channels - should delete channel successfully`() {
        val channel = createChannel()

        given()
            .`when`()
            .delete("/channels/${channel.channelId}")
            .then()
            .statusCode(204)

        given()
            .`when`()
            .get("/channels/${channel.channelId}")
            .then()
            .statusCode(404)
    }

    @Test
    fun `DELETE channels - should fail when channel does not exist`() {
        val nonExistentId = UUID.randomUUID()

        given()
            .`when`()
            .delete("/channels/$nonExistentId")
            .then()
            .statusCode(500)
    }

    @Test
    fun `POST routing-keys - should add routing key successfully`() {
        val channel = createChannel()

        given()
            .contentType(ContentType.JSON)
            .body("""{"routingKey": "new.routing.key"}""")
            .`when`()
            .post("/channels/${channel.channelId}/routing-keys")
            .then()
            .statusCode(204)

        given()
            .`when`()
            .get("/channels/${channel.channelId}")
            .then()
            .statusCode(200)
            .body("routingKeys", hasItem("new.routing.key"))
    }

    @Test
    fun `POST routing-keys - should fail when channel does not exist`() {
        val nonExistentId = UUID.randomUUID()

        given()
            .contentType(ContentType.JSON)
            .body("""{"routingKey": "new.routing.key"}""")
            .`when`()
            .post("/channels/$nonExistentId/routing-keys")
            .then()
            .statusCode(500)
    }

    @Test
    fun `POST routing-keys - should fail when routing key already exists`() {
        val channel = createChannel()
        val existingRoutingKey = channel.routingKeys.first()

        given()
            .contentType(ContentType.JSON)
            .body("""{"routingKey": "$existingRoutingKey"}""")
            .`when`()
            .post("/channels/${channel.channelId}/routing-keys")
            .then()
            .statusCode(500)
    }

    @Test
    fun `POST channels - should create FIFO channel successfully`() {
        val request =
            mapOf(
                "name" to "fifo-channel",
                "channelType" to ChannelType.QUEUE.name,
                "routingKeys" to listOf("fifo.key"),
                "consumptionType" to ConsumptionType.FIFO.name,
            )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/channels")
            .then()
            .statusCode(201)
            .body("consumptionType", equalTo("FIFO"))
    }

    @Test
    fun `POST channels - should create channel with multiple routing keys`() {
        val request =
            mapOf(
                "name" to "multi-routing-channel",
                "channelType" to ChannelType.QUEUE.name,
                "routingKeys" to listOf("key.one", "key.two", "key.three"),
                "consumptionType" to ConsumptionType.STANDARD.name,
            )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/channels")
            .then()
            .statusCode(201)
            .body("routingKeys", hasSize<String>(3))
            .body("routingKeys", hasItem("key.one"))
            .body("routingKeys", hasItem("key.two"))
            .body("routingKeys", hasItem("key.three"))
    }
}
