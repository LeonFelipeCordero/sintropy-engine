package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.repository.MessageRepository
import com.ph.syntropyengine.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

// TODO clean up all comments here
@Service
class PollingStandardQueue(
    override val messageRepository: MessageRepository
) : PollingQueue {

    @Transactional
    override fun poll(channelId: UUID, routingKey: String, pollingCount: Int): List<Message> {
        val messages = messageRepository
            .pollFromStandardChannelByRoutingKey(channelId, routingKey, pollingCount)
            .sortedBy { it.timestamp } // todo find a way to sort in database as the update unsort

        logger.info { "polled ${messages.size} messages for [${routing(channelId, routingKey)}]" }

        return messages
    }
    //TODO dequeue bach and and markAsFailed batch


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
//    suspend fun addConnection(channelId: UUID, routingKey: String) {
//       mutex.withLock {
//           queues.put(routing(channelId, routingKey), Channel(capacity = Channel.BUFFERED))
//       }
//    }
//    suspend fun stream(channelId: UUID, routingKey: String): List<Message> {
//        return emptyList()
//    }
}