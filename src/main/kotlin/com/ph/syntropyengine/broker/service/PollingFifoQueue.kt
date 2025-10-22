package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.repository.MessageRepository
import com.ph.syntropyengine.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class PollingFifoQueue(
    override val messageRepository: MessageRepository
) : PollingQueue {

    @Transactional
    override fun poll(channelId: UUID, routingKey: String, pollingCount: Int): List<Message> {
        val messages = messageRepository
            .pollFromFifoChannelByRoutingKey(channelId, routingKey, pollingCount)
            .sortedBy { it.timestamp }
        // todo find a way to sort in database as the update unsort
        // todo also this is the given external timestamp

        logger.info { "polled ${messages.map { it.messageId }} messages for [${routing(channelId, routingKey)}]" }
//        logger.info { "polled [${messages.size}] messages for ${loggingPair(channelId, routingKey)}" }

        return messages
    }
}