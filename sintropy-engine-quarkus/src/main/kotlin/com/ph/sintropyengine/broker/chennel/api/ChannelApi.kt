package com.ph.sintropyengine.broker.chennel.api

import com.ph.sintropyengine.broker.chennel.model.ChannelType
import com.ph.sintropyengine.broker.chennel.model.ConsumptionType
import com.ph.sintropyengine.broker.chennel.service.ChannelService
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

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
        return Response.status(Response.Status.CREATED).entity(channel).build()
    }

    @GET
    @Path("/{id}")
    fun findById(
        @PathParam("id") id: UUID,
    ): Response {
        val channel = channelService.findById(id)
        return if (channel != null) {
            Response.ok(channel).build()
        } else {
            Response.status(Response.Status.NOT_FOUND).build()
        }
    }

    @GET
    @Path("/name/{name}")
    fun findByName(
        @PathParam("name") name: String,
    ): Response {
        val channel = channelService.findByName(name)
        return if (channel != null) {
            Response.ok(channel).build()
        } else {
            Response.status(Response.Status.NOT_FOUND).build()
        }
    }

    @DELETE
    @Path("/{id}")
    fun deleteChannel(
        @PathParam("id") id: UUID,
    ): Response {
        channelService.deleteChannel(id)
        return Response.noContent().build()
    }

    @POST
    @Path("/{id}/routing-keys")
    fun addRoutingKey(
        @PathParam("id") id: UUID,
        routingKeyRequest: CreateNewRoutingKeyRequest,
    ): Response {
        channelService.addRoutingKey(id, routingKeyRequest.routingKey)
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
