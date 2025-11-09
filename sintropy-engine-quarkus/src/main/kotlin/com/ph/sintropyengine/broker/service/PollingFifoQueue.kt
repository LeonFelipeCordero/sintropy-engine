package com.ph.sintropyengine.broker.service

import com.ph.sintropyengine.broker.model.Message
import com.ph.sintropyengine.broker.repository.MessageRepository
import com.ph.sintropyengine.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class PollingFifoQueue(
    override val messageRepository: MessageRepository
) : PollingQueue {

    @Transactional
    override fun poll(channelId: UUID, routingKey: String, pollingCount: Int): List<Message> {
        val messages = messageRepository
            .pollFromFifoChannelByRoutingKey(channelId, routingKey, pollingCount)
            .sortedBy { it.timestamp }

//        logger.info { "polled ${messages.map { it.messageId }} messages for [${routing(channelId, routingKey)}]" }
        logger.info { "polled [${messages.size}] messages for [${routing(channelId, routingKey)}]" }

        return messages
    }
}