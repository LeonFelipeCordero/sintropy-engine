package com.ph.sintropyengine.broker.service

import com.ph.sintropyengine.broker.model.Consumer
import com.ph.sintropyengine.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = KotlinLogging.logger {}

@Singleton
class MessageRouter {
    private val mutex = Mutex()
    private val routingTable: MutableMap<String, MutableList<Consumer>> = mutableMapOf()

    suspend fun addConsumer(consumer: Consumer) {
        mutex.withLock {
            val consumers = routingTable[consumer.routing()]
            if (consumers == null) {
                routingTable[consumer.routing()] = mutableListOf(consumer)
                return
            }

            consumers.firstOrNull { it.consumerId == consumer.consumerId }
                ?: routingTable[consumer.routing()]!!.add(consumer)
        }
    }

    suspend fun removeConsumer(consumer: Consumer) {
        mutex.withLock {
            val consumers = routingTable[consumer.routing()]
            if (consumers == null) {
                logger.info { "Consumer not found routing table: ${consumer.routing()}" }
                return
            }

            routingTable[consumer.routing()]!!.removeIf { it.consumerId == consumer.consumerId }
            logger.info { "Removed consumer from routing table: ${consumer.consumerId}" }
        }
    }

    suspend fun getConsumersByRouting(channelId: UUID, routingKey: String): List<Consumer> {
        return routingTable[routing(channelId, routingKey)] ?: emptyList()
    }
}