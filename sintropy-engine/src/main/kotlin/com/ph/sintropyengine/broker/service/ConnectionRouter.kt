package com.ph.sintropyengine.broker.service

import com.ph.sintropyengine.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = KotlinLogging.logger {}

@Singleton
class ConnectionRouter(
    private val channelService: ChannelService
) {
    private val mutex = Mutex()

    private val routingTable: MutableMap<String, MutableList<String>> = mutableMapOf()

    private val connectionsTable: MutableMap<String, String> = mutableMapOf()

    suspend fun add(connectionId: String, channelName: String, routingKey: String) {
        val channel =
            channelService.findByName(channelName) ?: throw IllegalStateException("Channel $channelName not found")

        if (!channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("Routing key $routingKey not found in channel $channelName")
        }

        mutex.withLock {
            val routing = routing(channel.channelId!!, routingKey)

            val connections = routingTable[routing]

            connectionsTable[connectionId] = routing

            if (connections == null) {
                routingTable[routing] = mutableListOf(connectionId)
                return
            }

            connections.firstOrNull { it == connectionId } ?: routingTable[routing]!!.add(connectionId)
        }
    }

    suspend fun remove(connectionId: String) {
        mutex.withLock {
            val routing = connectionsTable[connectionId]
            if (routing == null) {
                logger.info { "Connection not found in connections table: $routing" }
                return
            }

            val connections = routingTable[routing]
            if (connections == null) {
                logger.info { "Connection not found routing table: $routing" }
                return
            }

            routingTable[routing]!!.removeIf { it == connectionId }
            connectionsTable.remove(connectionId)
            logger.info { "Removed Connection from routing table: $connectionId" }
        }
    }

    suspend fun getByRoutingKey(routing: String): List<String> {
        return mutex.withLock { routingTable[routing] ?: emptyList() }
    }
}