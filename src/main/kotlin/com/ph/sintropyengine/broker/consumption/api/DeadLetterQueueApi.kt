package com.ph.sintropyengine.broker.consumption.api

import com.ph.sintropyengine.broker.consumption.service.DeadLetterQueueService
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/dead-letter-queue")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class DeadLetterQueueApi(
    private val dlqService: DeadLetterQueueService,
) {
    @GET
    @Path("/channels/{channelName}/routing-keys/{routingKey}")
    fun listMessages(
        @PathParam("channelName") channelName: String,
        @PathParam("routingKey") routingKey: String,
        @QueryParam("pageSize") pageSize: Int?,
        @QueryParam("page") page: Int?,
    ): Response {
        val messages =
            dlqService.findByChannelAndRoutingKey(
                channelName = channelName,
                routingKey = routingKey,
                pageSize = pageSize ?: 100,
                page = page ?: 0,
            )
        return Response.ok(messages).build()
    }

    @POST
    @Path("/messages/{messageId}/recover")
    fun recoverSingleMessage(
        @PathParam("messageId") messageId: UUID,
    ): Response {
        val recoveredMessage = dlqService.recoverMessage(messageId)
        return Response.ok(recoveredMessage).build()
    }

    @POST
    @Path("/messages/recover")
    fun recoverMultipleMessages(request: RecoverMessagesRequest): Response {
        val recoveredMessages = dlqService.recoverMessages(request.messageIds)
        return Response.ok(recoveredMessages).build()
    }

    @POST
    @Path("/channels/{channelName}/routing-keys/{routingKey}/recover")
    fun recoverAllForChannel(
        @PathParam("channelName") channelName: String,
        @PathParam("routingKey") routingKey: String,
    ): Response {
        val recoveredMessages =
            dlqService.recoverAllForChannelAndRoutingKey(
                channelName = channelName,
                routingKey = routingKey,
            )
        return Response.ok(RecoverAllResponse(recoveredCount = recoveredMessages.size)).build()
    }
}

data class RecoverMessagesRequest(
    val messageIds: List<UUID>,
)

data class RecoverAllResponse(
    val recoveredCount: Int,
)
