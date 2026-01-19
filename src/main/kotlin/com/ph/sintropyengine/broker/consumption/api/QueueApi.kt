package com.ph.sintropyengine.broker.consumption.api

import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.api.response.MessageResponse
import com.ph.sintropyengine.broker.consumption.api.response.toResponse
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.consumption.service.PollingFifoQueue
import com.ph.sintropyengine.broker.consumption.service.PollingStandardQueue
import com.ph.sintropyengine.broker.producer.service.ProducerService
import com.ph.sintropyengine.broker.shared.observability.ObservabilityService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Path("/queues")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class QueueApi(
    private val channelService: ChannelService,
    private val producerService: ProducerService,
    private val pollingFifoQueue: PollingFifoQueue,
    private val pollingStandardQueue: PollingStandardQueue,
    private val observabilityService: ObservabilityService,
    private val messageRepository: MessageRepository,
) {
    data class PollRequest(
        val channelName: String,
        val routingKey: String,
        val pollingCount: Int,
    )

    @POST
    @Path("/poll")
    fun poll(request: PollRequest): Response {
        logger.debug {
            "Poll request received [channel=${request.channelName}, routingKey=${request.routingKey}, " +
                "pollingCount=${request.pollingCount}]"
        }

        val channel =
            channelService.findByName(request.channelName)
                ?: return Response.status(Response.Status.NOT_FOUND).entity("Channel not found").build()

        val consumptionType = channel.getConsumptionOrFail()
        val messages =
            when (consumptionType) {
                ConsumptionType.FIFO -> {
                    pollingFifoQueue.poll(channel.channelId!!, request.routingKey, request.pollingCount)
                }

                ConsumptionType.STANDARD -> {
                    pollingStandardQueue.poll(
                        channel.channelId!!,
                        request.routingKey,
                        request.pollingCount,
                    )
                }
            }

        observabilityService.recordMessagesPolled(
            channelName = request.channelName,
            routingKey = request.routingKey,
            consumptionType = consumptionType.name,
            count = messages.size,
        )

        logger.info {
            "Polled ${messages.size} messages [channel=${request.channelName}, routingKey=${request.routingKey}, " +
                "consumptionType=${consumptionType.name}]"
        }

        val producerIds = messages.map { it.producerId }.toSet()
        val producersById = producerService.findByIds(producerIds)
        val responses =
            messages.map { message ->
                val producer =
                    producersById[message.producerId]
                        ?: throw IllegalStateException("Producer ${message.producerId} not found")
                message.toResponse(request.channelName, producer.name)
            }

        return Response.ok(responses).build()
    }

    @POST
    @Path("/messages/{messageId}/failed")
    fun markAsFailed(
        @PathParam("messageId") messageId: UUID,
    ): Response {
        val message = messageRepository.findById(messageId)
            ?: return Response.status(Response.Status.NOT_FOUND).entity("Message not found").build()

        val channel = channelService.findById(message.channelId)

        logger.info { "Marking message as failed [messageId=$messageId, channel=${channel?.name}, routingKey=${message.routingKey}]" }

        pollingStandardQueue.markAsFailed(messageId)

        observabilityService.recordMessageFailed(
            channelName = channel!!.name,
            routingKey = message.routingKey,
        )

        return Response.noContent().build()
    }

    @DELETE
    @Path("/messages/{messageId}")
    fun dequeue(
        @PathParam("messageId") messageId: UUID,
    ): Response {
        val message = messageRepository.findById(messageId)
            ?: return Response.status(Response.Status.NOT_FOUND).entity("Message not found").build()

        val channel = channelService.findById(message.channelId)

        logger.info { "Dequeue request received [messageId=$messageId, channel=${channel?.name}, routingKey=${message.routingKey}]" }

        pollingStandardQueue.dequeue(messageId)

        observabilityService.recordMessageDequeued(
            channelName = channel!!.name,
            routingKey = message.routingKey,
        )

        return Response.noContent().build()
    }
}
