import com.ph.sintropyengine.broker.channel.service.ChannelLinkService
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
        return Response.status(Response.Status.CREATED).entity(link).build()
    }

    @GET
    @Path("/links/{linkId}")
    fun findById(
        @PathParam("linkId") linkId: UUID,
    ): Response {
        val link = channelLinkService.findById(linkId)
        return if (link != null) {
            Response.ok(link).build()
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
        return Response.ok(links).build()
    }

    @GET
    @Path("/{channelName}/links/incoming")
    fun getIncomingLinks(
        @PathParam("channelName") channelName: String,
    ): Response {
        val links = channelLinkService.getLinksToChannel(channelName)
        return Response.ok(links).build()
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
