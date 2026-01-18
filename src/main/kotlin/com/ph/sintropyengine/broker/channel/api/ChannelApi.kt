package com.ph.sintropyengine.broker.channel.api

import com.ph.sintropyengine.broker.channel.api.response.toResponse
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.service.ChannelService
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/channels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ChannelApi(
    private val channelService: ChannelService,
) {
    @POST
    fun createChannel(request: CreateChannelRequest): Response {
        val channel =
            channelService.createChannel(
                name = request.name,
                channelType = request.channelType,
                routingKeys = request.routingKeys,
                consumptionType = request.consumptionType,
            )
        return Response.status(Response.Status.CREATED).entity(channel.toResponse()).build()
    }

    @GET
    @Path("/{name}")
    fun findByName(
        @PathParam("name") name: String,
    ): Response =
        channelService
            .findByName(name)
            ?.let {
                Response.ok(it.toResponse()).build()
            } ?: Response.status(Response.Status.NOT_FOUND).build()

    @DELETE
    @Path("/{name}")
    fun deleteChannel(
        @PathParam("name") name: String,
    ): Response {
        channelService.findByName(name)
            ?: return Response.status(Response.Status.NOT_FOUND).build()
        channelService.deleteByName(name)
        return Response.noContent().build()
    }

    @POST
    @Path("/{name}/routing-keys")
    fun addRoutingKey(
        @PathParam("name") name: String,
        routingKeyRequest: CreateNewRoutingKeyRequest,
    ): Response {
        channelService.addRoutingKeyByName(name, routingKeyRequest.routingKey)
        return Response.noContent().build()
    }
}

data class CreateChannelRequest(
    val name: String,
    val channelType: ChannelType,
    val routingKeys: List<String>,
    val consumptionType: ConsumptionType?,
)

data class CreateNewRoutingKeyRequest(
    val routingKey: String,
)
