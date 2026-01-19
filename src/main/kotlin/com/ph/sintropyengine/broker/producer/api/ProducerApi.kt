package com.ph.sintropyengine.broker.producer.api

import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.api.response.toResponse
import com.ph.sintropyengine.broker.consumption.model.MessagePreStore
import com.ph.sintropyengine.broker.producer.api.response.toResponse
import com.ph.sintropyengine.broker.producer.service.ProducerService
import com.ph.sintropyengine.broker.shared.observability.ObservabilityService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

private val logger = KotlinLogging.logger {}

@Path("/producers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ProducerApi(
    private val producerService: ProducerService,
    private val channelService: ChannelService,
    private val observabilityService: ObservabilityService,
) {
    @POST
    fun createProducer(request: CreateProducerRequest): Response {
        logger.info { "Creating producer [name=${request.name}, channel=${request.channelName}]" }
        val producer = producerService.createProducer(request.name, request.channelName)
        logger.info { "Producer created [name=${request.name}, channel=${request.channelName}, id=${producer.producerId}]" }
        return Response
            .status(Response.Status.CREATED)
            .entity(producer.toResponse(request.channelName))
            .build()
    }

    @GET
    @Path("/{name}")
    fun findByName(
        @PathParam("name") name: String,
    ): Response {
        val producer = producerService.findByName(name)
        return if (producer != null) {
            val channel =
                channelService.findById(producer.channelId)
                    ?: throw IllegalStateException("Channel not found")
            Response.ok(producer.toResponse(channel.name)).build()
        } else {
            Response.status(Response.Status.NOT_FOUND).build()
        }
    }

    @GET
    @Path("/channel/{channelName}")
    fun findByChannel(
        @PathParam("channelName") channelName: String,
    ): Response {
        channelService.findByName(channelName)
            ?: return Response.status(Response.Status.NOT_FOUND).entity("Channel not found").build()
        val producers = producerService.findByChannel(channelName)
        val responses = producers.map { it.toResponse(channelName) }
        return Response.ok(responses).build()
    }

    @DELETE
    @Path("/{name}")
    fun deleteProducer(
        @PathParam("name") name: String,
    ): Response {
        producerService.findByName(name)
            ?: return Response.status(Response.Status.NOT_FOUND).build()
        producerService.deleteByName(name)
        return Response.noContent().build()
    }

    @POST
    @Path("/messages")
    fun publishMessage(request: PublishMessageRequest): Response {
        logger.debug {
            "Publishing message [channel=${request.channelName}, producer=${request.producerName}, " +
                "routingKey=${request.routingKey}]"
        }

        val publishedMessage = producerService.publishMessage(request.toMessagePreStore())

        observabilityService.recordMessagePublished(
            channelName = request.channelName,
            routingKey = request.routingKey,
            producerName = request.producerName,
        )

        logger.info {
            "Message published [messageId=${publishedMessage.messageId}, channel=${request.channelName}, " +
                "routingKey=${request.routingKey}, producer=${request.producerName}]"
        }

        return Response
            .status(Response.Status.CREATED)
            .entity(publishedMessage.toResponse(request.channelName, request.producerName))
            .build()
    }
}

data class CreateProducerRequest(
    val name: String,
    val channelName: String,
)

data class PublishMessageRequest(
    val channelName: String,
    val producerName: String,
    val routingKey: String,
    val message: String,
    val headers: String,
)

fun PublishMessageRequest.toMessagePreStore() =
    MessagePreStore(
        originMessageId = null,
        channelName = channelName,
        producerName = producerName,
        routingKey = routingKey,
        message = message,
        headers = headers,
    )
