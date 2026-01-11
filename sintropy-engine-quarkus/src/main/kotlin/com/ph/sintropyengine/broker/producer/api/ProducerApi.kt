package com.ph.sintropyengine.broker.producer.api

import com.ph.sintropyengine.broker.producer.service.ProducerService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.*

@Path("/producers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ProducerApi(
    private val producerService: ProducerService
) {

    @POST
    fun createProducer(request: CreateProducerRequest): Response {
        val producer = producerService.createProducer(request.name, request.channelName)
        return Response.status(Response.Status.CREATED).entity(producer).build()
    }

    @GET
    @Path("/{id}")
    fun findById(@PathParam("id") id: UUID): Response {
        val producer = producerService.findById(id)
        return if (producer != null) {
            Response.ok(producer).build()
        } else {
            Response.status(Response.Status.NOT_FOUND).build()
        }
    }

    @GET
    @Path("/channel/{channelName}")
    fun findByChannel(@PathParam("channelName") channelName: String): Response {
        val producers = producerService.findByChannel(channelName)
        return Response.ok(producers).build()
    }

    @DELETE
    @Path("/{id}")
    fun deleteProducer(@PathParam("id") id: UUID): Response {
        producerService.deleteProducer(id)
        return Response.noContent().build()
    }


    @POST
    @Path("/messages")
    fun publishMessage(request: PublishMessageRequest): Response {
        val publishedMessage = producerService.publishMessage(request)
        return Response.status(Response.Status.CREATED).entity(publishedMessage).build()
    }
}

data class CreateProducerRequest(
    val name: String,
    val channelName: String
)

data class PublishMessageRequest(
    val channelName: String,
    val producerName: String,
    val routingKey: String,
    val message: String,
    val headers: String
)
