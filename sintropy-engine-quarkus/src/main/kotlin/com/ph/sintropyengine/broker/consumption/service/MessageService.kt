package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.broker.chennel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.model.MessageLog
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped
import java.lang.IllegalStateException
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class MessageService(
    private val messageRepository: MessageRepository,
    private val channelService: ChannelService,
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
        to: OffsetDateTime?
    ): List<MessageLog> {
        val channel = (channelService.findByName(channelName)
            ?: throw IllegalStateException("Channel with name $channelName was not found"))

        if (!channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("Routing key $routingKey does not exist for channel $channelName")
        }

        logger.info { "Streaming messages for routing key [${channel.routing(routingKey)}] form $from and to $to" }

        return messageRepository.findMessageLogFromToByChannelIdAndRoutingKey(
            channelId = channel.channelId!!,
            routingKey = routingKey,
            from = from,
            to = to
        )
    }

    fun streamFromAll(
        channelName: String,
        routingKey: String,
    ): List<MessageLog> {
        val channel = (channelService.findByName(channelName)
            ?: throw IllegalStateException("Channel with name $channelName was not found"))

        if (!channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("Routing key $routingKey does not exist for channel $channelName")
        }

        logger.info { "Streaming ALL messages for routing key [${channel.routing(routingKey)}]" }

        return messageRepository.findAllMessagesByChannelIdAndRoutingKey(
            channelId = channel.channelId!!,
            routingKey = routingKey
        )
    }
}