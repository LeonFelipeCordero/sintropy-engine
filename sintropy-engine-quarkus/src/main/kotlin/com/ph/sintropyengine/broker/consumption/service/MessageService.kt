package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.broker.chennel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.model.MessageLog
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.websockets.next.OpenConnections
import jakarta.enterprise.context.ApplicationScoped
import java.lang.IllegalStateException
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class MessageService(
    private val messageRepository: MessageRepository,
    private val channelService: ChannelService,
    private var openConnections: OpenConnections
) {

    fun retriggerMessage(message: UUID): MessageLog {
        val message =
            messageRepository.findMessageLogById(message)
                ?: throw IllegalStateException("Message with id $message was not found")

        logger.info { "Retriggering message: $message" }

        return message
    }

    fun streamFromToByChannelIdAndRoutingKey(
        channelName: String,
        routingKey: String,
        from: OffsetDateTime,
        to: OffsetDateTime?,
        batchStreamInput: BatchStreamInput,
    ) {
        val channel = (channelService.findByName(channelName)
            ?: throw IllegalStateException("Channel with name $channelName was not found"))

        if (!channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("Routing key $routingKey does not exist for channel $channelName")
        }

        logger.info { "Streaming messages for routing key [${channel.routing(routingKey)}] form $from and to $to" }

        val connection = openConnections.findByConnectionId(batchStreamInput.connectionId)
            .orElseThrow { throw IllegalStateException("Connection with id ${batchStreamInput.connectionId} was not found") }

        var page = 0
        var batchMessages =
            messageRepository.findMessageLogFromToByChannelIdAndRoutingKey(
                channelId = channel.channelId!!,
                routingKey = routingKey,
                from = from,
                to = to,
                pageSize = batchStreamInput.batchSize,
                page = page
            )
        if (batchMessages.isNotEmpty()) {
            connection.sendTextAndAwait(batchMessages)
            page += 1
            Thread.sleep(batchStreamInput.delayInMs.toLong())
        }

        while (batchMessages.isNotEmpty()) {
            batchMessages = messageRepository.findMessageLogFromToByChannelIdAndRoutingKey(
                channelId = channel.channelId,
                routingKey = routingKey,
                from = from,
                to = to,
                pageSize = batchStreamInput.batchSize,
                page = page
            )
            connection.sendTextAndAwait(batchMessages)
            page += 1
            Thread.sleep(batchStreamInput.delayInMs.toLong())
        }
    }

    fun streamFromAll(
        channelName: String,
        routingKey: String,
        batchStreamInput: BatchStreamInput
    ) {
        val channel = (channelService.findByName(channelName)
            ?: throw IllegalStateException("Channel with name $channelName was not found"))

        if (!channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("Routing key $routingKey does not exist for channel $channelName")
        }

        logger.info { "Streaming ALL messages for routing key [${channel.routing(routingKey)}]" }

        val connection = openConnections.findByConnectionId(batchStreamInput.connectionId)
            .orElseThrow { throw IllegalStateException("Connection with id ${batchStreamInput.connectionId} was not found") }

        var page = 0
        var batchMessages =
            messageRepository.findAllMessagesByChannelIdAndRoutingKey(
                channelId = channel.channelId!!,
                routingKey = routingKey,
                pageSize = batchStreamInput.batchSize,
                page = page
            )

        if (batchMessages.isNotEmpty()) {
            connection.sendTextAndAwait(batchMessages)
            page += 1
            Thread.sleep(batchStreamInput.delayInMs.toLong())
        }

        while (batchMessages.isNotEmpty()) {
            batchMessages = messageRepository.findAllMessagesByChannelIdAndRoutingKey(
                channelId = channel.channelId,
                routingKey = routingKey,
                pageSize = batchStreamInput.batchSize,
                page = page
            )
            connection.sendTextAndAwait(batchMessages)
            page += 1
            Thread.sleep(batchStreamInput.delayInMs.toLong())
        }
    }
}

data class BatchStreamInput(
    val connectionId: String,
    val batchSize: Int = 500,
    val delayInMs: Int = 100,
)