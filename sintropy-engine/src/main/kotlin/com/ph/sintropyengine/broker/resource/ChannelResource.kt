package com.ph.sintropyengine.broker.resource

import com.ph.sintropyengine.broker.model.ChannelType
import com.ph.sintropyengine.broker.model.ConsumptionType
import com.ph.sintropyengine.broker.service.ChannelService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.*

@Path("/channels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ChannelResource(
    private val channelService: ChannelService
) {

    data class CreateChannelRequest(
        val name: String,
        val channelType: ChannelType,
        val routingKeys: List<String>,
        val consumptionType: ConsumptionType?,
    )

    @POST
    fun createChannel(request: CreateChannelRequest): Response {
        val channel = channelService.createChannel(
            name = request.name,
            channelType = request.channelType,
            routingKeys = request.routingKeys,
            consumptionType = request.consumptionType
        )
        return Response.status(Response.Status.CREATED).entity(channel).build()
    }

    @GET
    @Path("/{id}")
    fun findById(@PathParam("id") id: UUID): Response {
        val channel = channelService.findById(id)
        return if (channel != null) {
            Response.ok(channel).build()
        } else {
            Response.status(Response.Status.NOT_FOUND).build()
        }
    }

    @GET
    @Path("/name/{name}")
    fun findByName(@PathParam("name") name: String): Response {
        val channel = channelService.findByName(name)
        return if (channel != null) {
            Response.ok(channel).build()
        } else {
            Response.status(Response.Status.NOT_FOUND).build()
        }
    }

    @DELETE
    @Path("/{id}")
    fun deleteChannel(@PathParam("id") id: UUID): Response {
        channelService.deleteChannel(id)
        return Response.noContent().build()
    }

    @POST
    @Path("/{id}/routing-keys")
    fun addRoutingKey(@PathParam("id") id: UUID, routingKey: String): Response {
        channelService.addRoutingKey(id, routingKey)
        return Response.noContent().build()
    }
}
