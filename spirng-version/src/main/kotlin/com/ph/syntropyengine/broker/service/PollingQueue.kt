package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.model.MessageStatus
import com.ph.syntropyengine.broker.repository.MessageRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import org.springframework.transaction.annotation.Transactional

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