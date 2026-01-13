package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.model.DeadLetterMessage
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.repository.DeadLetterQueueRepository
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class DeadLetterQueueService(
    private val dlqRepository: DeadLetterQueueRepository,
    private val messageRepository: MessageRepository,
    private val channelService: ChannelService,
) {
    fun findByChannelAndRoutingKey(
        channelName: String,
        routingKey: String,
        pageSize: Int = 100,
        page: Int = 0,
    ): List<DeadLetterMessage> {
        val channel =
            channelService.findByName(channelName)
                ?: throw IllegalStateException("Channel with name $channelName not found")

        if (!channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("Routing key $routingKey does not exist for channel $channelName")
        }

        return dlqRepository.findByChannelIdAndRoutingKey(
            channelId = channel.channelId!!,
            routingKey = routingKey,
            pageSize = pageSize,
            page = page,
        )
    }

    @Transactional
    fun recoverMessage(messageId: UUID): Message {
        val dlqEntry =
            dlqRepository.findByMessageId(messageId)
                ?: throw IllegalStateException("Message with id $messageId not found in dead letter queue")

        logger.info { "Recovering message ${dlqEntry.messageId} from DLQ" }

        dlqRepository.delete(dlqEntry.dlqEntryId)

        return messageRepository.reinsertFromDlq(dlqEntry)
    }

    @Transactional
    fun recoverMessages(messageIds: List<UUID>): List<Message> {
        val dlqEntries = messageIds.mapNotNull { dlqRepository.findByMessageId(it) }

        if (dlqEntries.isEmpty()) {
            throw IllegalStateException("No messages found in dead letter queue for provided IDs")
        }

        logger.info { "Recovering ${dlqEntries.size} messages from DLQ" }

        dlqRepository.deleteByIds(dlqEntries.map { it.dlqEntryId })

        return dlqEntries.map { messageRepository.reinsertFromDlq(it) }
    }

    @Transactional
    fun recoverAllForChannelAndRoutingKey(
        channelName: String,
        routingKey: String,
    ): List<Message> {
        val channel =
            channelService.findByName(channelName)
                ?: throw IllegalStateException("Channel with name $channelName not found")

        if (!channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("Routing key $routingKey does not exist for channel $channelName")
        }

        val dlqEntries =
            dlqRepository.findAllByChannelIdAndRoutingKey(
                channelId = channel.channelId!!,
                routingKey = routingKey,
            )

        if (dlqEntries.isEmpty()) {
            logger.info { "No messages to recover for $channelName/$routingKey" }
            return emptyList()
        }

        logger.info { "Recovering ${dlqEntries.size} messages for $channelName/$routingKey from DLQ" }

        dlqRepository.deleteByChannelIdAndRoutingKey(channel.channelId, routingKey)

        return dlqEntries.map { messageRepository.reinsertFromDlq(it) }
    }
}
