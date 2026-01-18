package com.ph.sintropyengine.broker.consumption.api

import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.api.response.DeadLetterMessageResponse
import com.ph.sintropyengine.broker.consumption.api.response.MessageResponse
import com.ph.sintropyengine.broker.consumption.api.response.toResponse
import com.ph.sintropyengine.broker.consumption.service.DeadLetterQueueService
import com.ph.sintropyengine.broker.producer.service.ProducerService
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
    private val channelService: ChannelService,
    private val producerService: ProducerService,
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
        val producerIds = messages.map { it.producerId }.toSet()
        val producersById = producerService.findByIds(producerIds)
        val responses =
            messages.map { message ->
                val producer =
                    producersById[message.producerId]
                        ?: throw IllegalStateException("Producer ${message.producerId} not found")
                message.toResponse(channelName, producer.name)
            }
        return Response.ok(responses).build()
    }

    @POST
    @Path("/messages/{messageId}/recover")
    fun recoverSingleMessage(
        @PathParam("messageId") messageId: UUID,
    ): Response {
        val recoveredMessage = dlqService.recoverMessage(messageId)
        val channel =
            channelService.findById(recoveredMessage.channelId)
                ?: throw IllegalStateException("Channel not found")
        val producer =
            producerService.findById(recoveredMessage.producerId)
                ?: throw IllegalStateException("Producer not found")
        return Response.ok(recoveredMessage.toResponse(channel.name, producer.name)).build()
    }

    @POST
    @Path("/messages/recover")
    fun recoverMultipleMessages(request: RecoverMessagesRequest): Response {
        val recoveredMessages = dlqService.recoverMessages(request.messageIds)

        val channelIds = recoveredMessages.map { it.channelId }.toSet()
        val producerIds = recoveredMessages.map { it.producerId }.toSet()
        val channelsById = channelService.findByIds(channelIds)
        val producersById = producerService.findByIds(producerIds)

        val responses =
            recoveredMessages.map { message ->
                val channel =
                    channelsById[message.channelId]
                        ?: throw IllegalStateException("Channel ${message.channelId} not found")
                val producer =
                    producersById[message.producerId]
                        ?: throw IllegalStateException("Producer ${message.producerId} not found")
                message.toResponse(channel.name, producer.name)
            }
        return Response.ok(responses).build()
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
