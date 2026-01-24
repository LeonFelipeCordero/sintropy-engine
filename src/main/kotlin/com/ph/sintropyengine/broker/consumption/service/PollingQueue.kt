package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.model.MessageStatus
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

interface PollingQueue {
    val messageRepository: MessageRepository

    @Transactional
    fun poll(
        channelId: UUID,
        routingKey: String,
        pollingCount: Int = 1,
    ): List<Message>

    @Transactional
    fun markAsFailed(messageId: UUID) {
        messageRepository.markAsFailed(messageId)
        logger.info { "marked message as failed $messageId" }
    }

    @Transactional
    fun markAsFailedBulk(messageIds: List<UUID>): BulkOperationResult {
        if (messageIds.isEmpty()) {
            return BulkOperationResult(processed = emptyList())
        }

        val failedMessages = messageRepository.markAsFailedBulk(messageIds).map { it.messageId }

        logger.debug { "marked [$failedMessages] as failed in bulk" }
        return BulkOperationResult(processed = failedMessages)
    }

    @Transactional
    fun dequeue(messageId: UUID) {
        messageRepository.findById(messageId)?.also {
            if (it.status == MessageStatus.READY) {
                throw IllegalStateException("Message with id $messageId is still in status READY")
            }

            messageRepository.dequeue(messageId)

            logger.debug { "dequeue message $messageId" }
        } ?: throw IllegalStateException("Message with id $messageId not found")
    }

    @Transactional
    fun dequeueBulk(messageIds: List<UUID>): BulkOperationResult {
        if (messageIds.isEmpty()) {
            return BulkOperationResult(processed = emptyList())
        }

        val existingMessages = messageRepository.findByIds(messageIds)

        val messagesInReady = existingMessages.filter { it.status == MessageStatus.READY }
        if (messagesInReady.isNotEmpty()) {
            throw IllegalStateException("Some Messages [${messagesInReady.map { it.messageId }}] are still in status READY")
        }

        val dequeueBulk = messageRepository.dequeueBulk(messageIds).map { it.messageId }

        logger.debug { "dequeued [$dequeueBulk]" }
        return BulkOperationResult(processed = dequeueBulk)
    }
}

data class BulkOperationResult(
    val processed: List<UUID>,
)
