package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class PollingFifoQueue(
    override val messageRepository: MessageRepository,
) : PollingQueue {
    @Transactional
    override fun poll(
        channelId: UUID,
        routingKey: String,
        pollingCount: Int,
    ): List<Message> {
        val messages =
            messageRepository
                .pollFromFifoChannelByRoutingKey(channelId, routingKey, pollingCount)
                .sortedBy { it.timestamp }

        logger.debug { "polled [${messages.size}] messages for [${routing(channelId, routingKey)}]" }

        return messages
    }
}
