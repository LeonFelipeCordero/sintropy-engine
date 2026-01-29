package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class PollingFifoQueue(
    override val messageRepository: MessageRepository,
) : PollingQueue {
    @WithSpan("PollingFifoQueue.poll")
    @Transactional
    override fun poll(
        @SpanAttribute("channel.id") channelId: UUID,
        @SpanAttribute("routing.key") routingKey: String,
        @SpanAttribute("polling.count") pollingCount: Int,
    ): List<Message> {
        val messages =
            messageRepository
                .pollFromFifoChannelByRoutingKey(channelId, routingKey, pollingCount)
                .sortedBy { it.timestamp }

        logger.info { "polled [${messages.size}] messages for [${routing(channelId, routingKey)}]" }

        return messages
    }

    @WithSpan("PollingFifoQueue.markAsFailed")
    @Transactional
    override fun markAsFailed(@SpanAttribute("message.id") messageId: UUID) {
        super.markAsFailed(messageId)
    }

    @WithSpan("PollingFifoQueue.markAsFailedBulk")
    @Transactional
    override fun markAsFailedBulk(messageIds: List<UUID>): BulkOperationResult {
        return super.markAsFailedBulk(messageIds)
    }

    @WithSpan("PollingFifoQueue.dequeue")
    @Transactional
    override fun dequeue(@SpanAttribute("message.id") messageId: UUID) {
        super.dequeue(messageId)
    }

    @WithSpan("PollingFifoQueue.dequeueBulk")
    @Transactional
    override fun dequeueBulk(messageIds: List<UUID>): BulkOperationResult {
        return super.dequeueBulk(messageIds)
    }
}
