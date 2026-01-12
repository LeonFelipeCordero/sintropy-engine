package com.ph.sintropyengine.broker.channel.api

import com.ph.sintropyengine.IntegrationTestBase
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class ChannelLinkApiTest : IntegrationTestBase() {
    @BeforeEach
    fun setUp() {
        clean()
    }

    @Nested
    inner class CreateLink {
        @Test
        fun `should return 201 when link created successfully`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()

            val request =
                mapOf(
                    "sourceChannelName" to source.name,
                    "targetChannelName" to target.name,
                    "sourceRoutingKey" to source.routingKeys.first(),
                    "targetRoutingKey" to target.routingKeys.first(),
                )

            given()
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/channels/links")
                .then()
                .statusCode(201)
                .body("channelLinkId", notNullValue())
                .body("sourceChannelId", equalTo(source.channelId.toString()))
                .body("targetChannelId", equalTo(target.channelId.toString()))
                .body("enabled", equalTo(true))
        }

        @Test
        fun `should return 500 when link validation fails`() {
            val source = createStandardQueueChannel()
            val target = createFifoQueueChannel()

            val request =
                mapOf(
                    "sourceChannelName" to source.name,
                    "targetChannelName" to target.name,
                    "sourceRoutingKey" to source.routingKeys.first(),
                    "targetRoutingKey" to target.routingKeys.first(),
                )

            given()
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/channels/links")
                .then()
                .statusCode(500)
        }

        @Test
        fun `should return 500 when channel not found`() {
            val target = createStandardQueueChannel()

            val request =
                mapOf(
                    "sourceChannelName" to "non-existent",
                    "targetChannelName" to target.name,
                    "sourceRoutingKey" to "some.key",
                    "targetRoutingKey" to target.routingKeys.first(),
                )

            given()
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/channels/links")
                .then()
                .statusCode(500)
        }
    }

    @Nested
    inner class GetLinkById {
        @Test
        fun `should return 200 when link exists`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val link = createChannelLink(source, target)

            given()
                .`when`()
                .get("/channels/links/${link.channelLinkId}")
                .then()
                .statusCode(200)
                .body("channelLinkId", equalTo(link.channelLinkId.toString()))
        }

        @Test
        fun `should return 404 when link does not exist`() {
            given()
                .`when`()
                .get("/channels/links/${UUID.randomUUID()}")
                .then()
                .statusCode(404)
        }
    }

    @Nested
    inner class GetOutgoingLinks {
        @Test
        fun `should return 200 with links`() {
            val source = createStandardQueueChannel()
            val target1 = createStandardQueueChannel()
            val target2 = createStandardQueueChannel()

            createChannelLink(source, target1)
            createChannelLink(source, target2)

            given()
                .`when`()
                .get("/channels/${source.name}/links/outgoing")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(2))
        }

        @Test
        fun `should return 200 with empty list when no links`() {
            val channel = createStandardQueueChannel()

            given()
                .`when`()
                .get("/channels/${channel.name}/links/outgoing")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(0))
        }
    }

    @Nested
    inner class GetIncomingLinks {
        @Test
        fun `should return 200 with links`() {
            val source1 = createStandardQueueChannel()
            val source2 = createStandardQueueChannel()
            val target = createStandardQueueChannel()

            createChannelLink(source1, target)
            createChannelLink(source2, target)

            given()
                .`when`()
                .get("/channels/${target.name}/links/incoming")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(2))
        }

        @Test
        fun `should return 200 with empty list when no links`() {
            val channel = createStandardQueueChannel()

            given()
                .`when`()
                .get("/channels/${channel.name}/links/incoming")
                .then()
                .statusCode(200)
                .body("$", hasSize<Any>(0))
        }
    }

    @Nested
    inner class DeleteLink {
        @Test
        fun `should return 204 when link deleted`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val link = createChannelLink(source, target)

            given()
                .`when`()
                .delete("/channels/links/${link.channelLinkId}")
                .then()
                .statusCode(204)
        }

        @Test
        fun `should return 500 when link does not exist`() {
            given()
                .`when`()
                .delete("/channels/links/${UUID.randomUUID()}")
                .then()
                .statusCode(500)
        }
    }

    @Nested
    inner class EnableLink {
        @Test
        fun `should return 204 when link enabled`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val link = createChannelLink(source, target)
            channelLinkRepository.setEnabled(link.channelLinkId!!, false)

            given()
                .`when`()
                .put("/channels/links/${link.channelLinkId}/enable")
                .then()
                .statusCode(204)
        }

        @Test
        fun `should return 500 when link does not exist`() {
            given()
                .`when`()
                .put("/channels/links/${UUID.randomUUID()}/enable")
                .then()
                .statusCode(500)
        }
    }

    @Nested
    inner class DisableLink {
        @Test
        fun `should return 204 when link disabled`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val link = createChannelLink(source, target)

            given()
                .`when`()
                .put("/channels/links/${link.channelLinkId}/disable")
                .then()
                .statusCode(204)
        }

        @Test
        fun `should return 500 when link does not exist`() {
            given()
                .`when`()
                .put("/channels/links/${UUID.randomUUID()}/disable")
                .then()
                .statusCode(500)
        }
    }
}
