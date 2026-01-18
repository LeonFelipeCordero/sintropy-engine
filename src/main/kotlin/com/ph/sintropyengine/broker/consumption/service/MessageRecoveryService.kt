package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.api.response.MessageLogResponse
import com.ph.sintropyengine.broker.consumption.api.response.toResponse
import com.ph.sintropyengine.broker.consumption.model.MessageLog
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.producer.service.ProducerService
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.websockets.next.OpenConnections
import jakarta.enterprise.context.ApplicationScoped
import java.lang.IllegalStateException
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class MessageRecoveryService(
    private val messageRepository: MessageRepository,
    private val channelService: ChannelService,
    private val producerService: ProducerService,
    private var openConnections: OpenConnections,
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
        val channel = channelService.findByNameAndRoutingKeyStrict(channelName, routingKey)

        logger.info { "Streaming messages for routing key [${channel.routing(routingKey)}] form $from and to $to" }

        val connection =
            openConnections
                .findByConnectionId(batchStreamInput.connectionId)
                .orElseThrow { throw IllegalStateException("Connection with id ${batchStreamInput.connectionId} was not found") }

        var page = 0
        var batchMessages =
            messageRepository.findMessageLogFromToByChannelIdAndRoutingKey(
                channelId = channel.channelId!!,
                routingKey = routingKey,
                from = from,
                to = to,
                pageSize = batchStreamInput.batchSize,
                page = page,
            )
        if (batchMessages.isNotEmpty()) {
            connection.sendTextAndAwait(toMessageLogResponses(batchMessages, channelName))
            page += 1
            Thread.sleep(batchStreamInput.delayInMs.toLong())
        }

        while (batchMessages.isNotEmpty()) {
            batchMessages =
                messageRepository.findMessageLogFromToByChannelIdAndRoutingKey(
                    channelId = channel.channelId,
                    routingKey = routingKey,
                    from = from,
                    to = to,
                    pageSize = batchStreamInput.batchSize,
                    page = page,
                )
            connection.sendTextAndAwait(toMessageLogResponses(batchMessages, channelName))
            page += 1
            Thread.sleep(batchStreamInput.delayInMs.toLong())
        }
    }

    fun streamFromAll(
        channelName: String,
        routingKey: String,
        batchStreamInput: BatchStreamInput,
    ) {
        val channel = channelService.findByNameAndRoutingKeyStrict(channelName, routingKey)

        logger.info { "Streaming ALL messages for routing key [${channel.routing(routingKey)}]" }

        val connection =
            openConnections
                .findByConnectionId(batchStreamInput.connectionId)
                .orElseThrow { throw IllegalStateException("Connection with id ${batchStreamInput.connectionId} was not found") }

        var page = 0
        var batchMessages =
            messageRepository.findAllMessagesByChannelIdAndRoutingKey(
                channelId = channel.channelId!!,
                routingKey = routingKey,
                pageSize = batchStreamInput.batchSize,
                page = page,
            )

        if (batchMessages.isNotEmpty()) {
            connection.sendTextAndAwait(toMessageLogResponses(batchMessages, channelName))
            page += 1
            Thread.sleep(batchStreamInput.delayInMs.toLong())
        }

        while (batchMessages.isNotEmpty()) {
            batchMessages =
                messageRepository.findAllMessagesByChannelIdAndRoutingKey(
                    channelId = channel.channelId,
                    routingKey = routingKey,
                    pageSize = batchStreamInput.batchSize,
                    page = page,
                )
            connection.sendTextAndAwait(toMessageLogResponses(batchMessages, channelName))
            page += 1
            Thread.sleep(batchStreamInput.delayInMs.toLong())
        }
    }

    private fun toMessageLogResponses(
        messages: List<MessageLog>,
        channelName: String,
    ): List<MessageLogResponse> {
        if (messages.isEmpty()) return emptyList()

        val producerIds = messages.map { it.producerId }.toSet()
        val producersById = producerService.findByIds(producerIds)

        return messages.map { msg ->
            val producer =
                producersById[msg.producerId]
                    ?: throw IllegalStateException("Producer ${msg.producerId} not found")
            msg.toResponse(channelName, producer.name)
        }
    }
}

data class BatchStreamInput(
    val connectionId: String,
    val batchSize: Int = 500,
    val delayInMs: Int = 100,
)
