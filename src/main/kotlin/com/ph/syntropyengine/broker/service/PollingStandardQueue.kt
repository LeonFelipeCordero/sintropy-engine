package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.model.MessageStatus
import com.ph.syntropyengine.broker.repository.MessageRepository
import com.ph.syntropyengine.utils.Patterns.loggingPair
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

// TODO clean up all comments here
@Service
class PollingStandardQueue(
    private val messageRepository: MessageRepository
) {
//     Key: ChannelId|RoutingKey, value channel where messages are published and park for client consumption
//    private val queues = mutableMapOf<String, Channel<Message>>()
//    private val mutex = Mutex()

//    suspend fun queue(message: Message) {
//        mutex.withLock {
//            val channel = queues[message.routing()]
//            if (channel == null) {
//                queues[message.routing()] = Channel(capacity = Channel.BUFFERED)
//            }
//
//            queues[message.routing()]!!.send(message)
//        }
//    }

    @Transactional
    fun poll(channelId: UUID, routingKey: String, pollingCount: Int = 1): List<Message> {
        val messages = messageRepository
            .pollFromQueueAndRoutingKey(channelId, routingKey, pollingCount)
            .sortedBy { it.timestamp } // todo find a way to sort in database as the update unsort

        logger.info { "polled ${messages.size} messages for ${loggingPair(channelId, routingKey)}" }

        return messages
    }

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
    //TODO dequeue bach and and markAsFailed batch

//    suspend fun addConnection(channelId: UUID, routingKey: String) {
//       mutex.withLock {
//           queues.put(routing(channelId, routingKey), Channel(capacity = Channel.BUFFERED))
//       }
//    }

//    suspend fun stream(channelId: UUID, routingKey: String): List<Message> {
//        TODO implement streaming on deman
//        return emptyList()
//    }
}