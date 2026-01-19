package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.model.DeadLetterMessage
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.repository.DeadLetterQueueRepository
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.shared.observability.ObservabilityService
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
    private val observabilityService: ObservabilityService,
) {
    fun findByChannelAndRoutingKey(
        channelName: String,
        routingKey: String,
        pageSize: Int = 100,
        page: Int = 0,
    ): List<DeadLetterMessage> {
        val channel = channelService.findByNameAndRoutingKeyStrict(channelName, routingKey)

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

        val channel = channelService.findById(dlqEntry.channelId)
            ?: throw IllegalStateException("Channel with id ${dlqEntry.channelId} not found")

        dlqRepository.delete(dlqEntry.dlqEntryId)
        val recovered = messageRepository.reinsertFromDlq(dlqEntry)

        observabilityService.recordMessageRecoveredFromDlq(
            channelName = channel.name,
            routingKey = dlqEntry.routingKey,
            count = 1,
        )

        logger.info {
            "Recovered message from DLQ [messageId=${dlqEntry.messageId}, channel=${channel.name}, routingKey=${dlqEntry.routingKey}]"
        }

        return recovered
    }

    @Transactional
    fun recoverMessages(messageIds: List<UUID>): List<Message> {
        val dlqEntries = messageIds.mapNotNull { dlqRepository.findByMessageId(it) }

        if (dlqEntries.isEmpty()) {
            throw IllegalStateException("No messages found in dead letter queue for provided IDs")
        }

        val channelIds = dlqEntries.map { it.channelId }.toSet()
        val channelsById = channelService.findByIds(channelIds)

        dlqRepository.deleteByIds(dlqEntries.map { it.dlqEntryId })

        val recovered = dlqEntries.map { messageRepository.reinsertFromDlq(it) }

        dlqEntries.groupBy { Pair(it.channelId, it.routingKey) }.forEach { (key, entries) ->
            val (channelId, routingKey) = key
            val channelName = channelsById[channelId]?.name ?: "unknown"
            observabilityService.recordMessageRecoveredFromDlq(
                channelName = channelName,
                routingKey = routingKey,
                count = entries.size,
            )
        }

        logger.info { "Recovered ${recovered.size} messages from DLQ [messageIds=$messageIds]" }

        return recovered
    }

    @Transactional
    fun recoverAllForChannelAndRoutingKey(
        channelName: String,
        routingKey: String,
    ): List<Message> {
        val channel = channelService.findByNameAndRoutingKeyStrict(channelName, routingKey)

        val dlqEntries =
            dlqRepository.findAllByChannelIdAndRoutingKey(
                channelId = channel.channelId!!,
                routingKey = routingKey,
            )

        if (dlqEntries.isEmpty()) {
            logger.debug { "No messages to recover from DLQ [channel=$channelName, routingKey=$routingKey]" }
            return emptyList()
        }

        dlqRepository.deleteByChannelIdAndRoutingKey(channel.channelId, routingKey)

        val recovered = dlqEntries.map { messageRepository.reinsertFromDlq(it) }

        observabilityService.recordMessageRecoveredFromDlq(
            channelName = channelName,
            routingKey = routingKey,
            count = recovered.size,
        )

        logger.info {
            "Recovered ${recovered.size} messages from DLQ [channel=$channelName, routingKey=$routingKey]"
        }

        return recovered
    }
}
