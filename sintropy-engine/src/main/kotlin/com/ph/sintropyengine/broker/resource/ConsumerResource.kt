package com.ph.sintropyengine.broker.resource

import com.ph.sintropyengine.broker.service.ConsumerService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.*

@Path("/consumers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ConsumerResource(
    private val consumerService: ConsumerService
) {

    // TODO: Connections are via websockets
//    data class CreateConsumerRequest(
//        val channelName: String,
//        val routingKey: String,
//        val connectionId: String
//    )
//
//    @POST
//    suspend fun createConsumer(request: CreateConsumerRequest): Response {
//        val consumer = consumerService.createConsumer(request.channelName, request.routingKey, request.connectionId)
//        return Response.status(Response.Status.CREATED).entity(consumer).build()
//    }

//    @GET
//    @Path("/{id}")
//    fun findById(@PathParam("id") id: UUID): Response {
//        val consumer = consumerService.findById(id)
//        return if (consumer != null) {
//            Response.ok(consumer).build()
//        } else {
//            Response.status(Response.Status.NOT_FOUND).build()
//        }
//    }

//    @GET
//    @Path("/channel/{channelName}")
//    fun findByChannel(@PathParam("channelName") channelName: String): Response {
//        val consumers = consumerService.findByChannel(channelName)
//        return Response.ok(consumers).build()
//    }

//    @DELETE
//    @Path("/{id}")
//    fun deleteConsumer(@PathParam("id") id: UUID): Response {
//        consumerService.deleteConsumer(id)
//        return Response.noContent().build()
//    }
}
