package com.ph.sintropyengine.broker.resource

import com.ph.sintropyengine.broker.model.ConsumptionType
import com.ph.sintropyengine.broker.service.ChannelService
import com.ph.sintropyengine.broker.service.PollingFifoQueue
import com.ph.sintropyengine.broker.service.PollingStandardQueue
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.*

@Path("/queues")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class QueueResource(
    private val channelService: ChannelService,
    private val pollingFifoQueue: PollingFifoQueue,
    private val pollingStandardQueue: PollingStandardQueue
) {

    data class PollRequest(
        val channelName: String,
        val routingKey: String,
        val pollingCount: Int
    )

    @POST
    @Path("/poll")
    fun poll(request: PollRequest): Response {
        // TODO: How to avoid this round-trip to the database
        val channel = channelService.findByName(request.channelName)
            ?: return Response.status(Response.Status.NOT_FOUND).entity("Channel not found").build()

        // TODO this should be done in the service
        val consumptionType = channel.getConsumptionOrFail()
        val messages = when (consumptionType) {
            ConsumptionType.FIFO -> pollingFifoQueue.poll(channel.channelId!!, request.routingKey, request.pollingCount)
            ConsumptionType.STANDARD -> pollingStandardQueue.poll(
                channel.channelId!!,
                request.routingKey,
                request.pollingCount
            )
        }

        return Response.ok(messages).build()
    }

    @POST
    @Path("/messages/{messageId}/failed")
    fun markAsFailed(@PathParam("messageId") messageId: UUID): Response {
        pollingStandardQueue.markAsFailed(messageId)
        return Response.noContent().build()
    }

    @DELETE
    @Path("/messages/{messageId}")
    fun dequeue(@PathParam("messageId") messageId: UUID): Response {
        pollingStandardQueue.dequeue(messageId)
        return Response.noContent().build()
    }
}
