package com.ph.sintropyengine.broker.channel.api

import com.ph.sintropyengine.broker.channel.api.response.toResponse
import com.ph.sintropyengine.broker.channel.service.ChannelLinkService
import com.ph.sintropyengine.broker.channel.service.ChannelService
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/channels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ChannelLinkApi(
    private val channelLinkService: ChannelLinkService,
    private val channelService: ChannelService,
) {
    @POST
    @Path("/links")
    fun createLink(request: CreateChannelLinkRequest): Response {
        val link =
            channelLinkService.linkChannels(
                sourceChannelName = request.sourceChannelName,
                targetChannelName = request.targetChannelName,
                sourceRoutingKey = request.sourceRoutingKey,
                targetRoutingKey = request.targetRoutingKey,
            )
        return Response
            .status(Response.Status.CREATED)
            .entity(link.toResponse(request.sourceChannelName, request.targetChannelName))
            .build()
    }

    @GET
    @Path("/links/{linkId}")
    fun findById(
        @PathParam("linkId") linkId: UUID,
    ): Response {
        val link = channelLinkService.findByUUID(linkId)

        val (sourceChannel, targetChannel) =
            link?.let {
                val source =
                    channelService.findById(link.sourceChannelId)
                        ?: throw IllegalStateException("Source channel ${link.sourceChannelId} not found for link $linkId")
                val target =
                    channelService.findById(link.targetChannelId)
                        ?: throw IllegalStateException("Target channel ${link.sourceChannelId} not found for link $linkId")

                Pair(source, target)
            } ?: Pair(null, null)

        return if (link != null) {
            Response.ok(link.toResponse(sourceChannel!!.name, targetChannel!!.name)).build()
        } else {
            Response.status(Response.Status.NOT_FOUND).build()
        }
    }

    @GET
    @Path("/{channelName}/links/outgoing")
    fun getOutgoingLinks(
        @PathParam("channelName") channelName: String,
    ): Response {
        val links = channelLinkService.getLinksFromChannel(channelName)
        val responses =
            links.map { link ->
                val targetChannel =
                    channelService.findById(link.targetChannelId)
                        ?: throw IllegalStateException("Target channel not found")
                link.toResponse(channelName, targetChannel.name)
            }
        return Response.ok(responses).build()
    }

    @GET
    @Path("/{channelName}/links/incoming")
    fun getIncomingLinks(
        @PathParam("channelName") channelName: String,
    ): Response {
        val links = channelLinkService.getLinksToChannel(channelName)
        val responses =
            links.map { link ->
                val sourceChannel =
                    channelService.findById(link.sourceChannelId)
                        ?: throw IllegalStateException("Source channel not found")
                link.toResponse(sourceChannel.name, channelName)
            }
        return Response.ok(responses).build()
    }

    @DELETE
    @Path("/links/{linkId}")
    fun deleteLink(
        @PathParam("linkId") linkId: UUID,
    ): Response {
        channelLinkService.unlinkChannels(linkId)
        return Response.noContent().build()
    }

    @PUT
    @Path("/links/{linkId}/enable")
    fun enableLink(
        @PathParam("linkId") linkId: UUID,
    ): Response {
        channelLinkService.enableLink(linkId)
        return Response.noContent().build()
    }

    @PUT
    @Path("/links/{linkId}/disable")
    fun disableLink(
        @PathParam("linkId") linkId: UUID,
    ): Response {
        channelLinkService.disableLink(linkId)
        return Response.noContent().build()
    }
}

data class CreateChannelLinkRequest(
    val sourceChannelName: String,
    val targetChannelName: String,
    val sourceRoutingKey: String,
    val targetRoutingKey: String,
)
