package com.ph.sintropyengine.broker.service

import com.ph.sintropyengine.broker.model.Message
import com.ph.sintropyengine.broker.model.MessageStatus
import com.ph.sintropyengine.broker.repository.MessageRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

interface PollingQueue {

    val messageRepository: MessageRepository

    @Transactional
    fun poll(channelId: UUID, routingKey: String, pollingCount: Int = 1): List<Message>

    @Transactional
    fun markAsFailed(messageId: UUID) {
        messageRepository.markAsFailed(messageId)
        logger.info { "marked message as failed $messageId" }
    }

    @Transactional
    fun dequeue(messageId: UUID) {
        messageRepository.findById(messageId)?.also {
            if (it.status == MessageStatus.READY) {
                throw IllegalStateException("Message with id $messageId is still in status READY")
            }

            messageRepository.dequeue(messageId)

            logger.info { "dequeue message $messageId" }

        } ?: throw IllegalStateException("Message with id $messageId not found")
    }
}