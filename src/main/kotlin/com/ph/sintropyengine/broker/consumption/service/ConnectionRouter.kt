package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = KotlinLogging.logger {}

data class RoutingTarget(
    val channelName: String,
    val routingKey: String,
)

@Singleton
class ConnectionRouter(
    private val channelService: ChannelService,
) {
    private val mutex = Mutex()

    private val routingTable: MutableMap<String, MutableList<String>> = mutableMapOf()

    private val connectionsTable: MutableMap<String, String> = mutableMapOf()

    private val connectionMetadata: MutableMap<String, RoutingTarget> = mutableMapOf()

    suspend fun add(
        connectionId: String,
        channelName: String,
        routingKey: String,
    ) {
        val channel = channelService.findByNameAndRoutingKeyStrict(channelName, routingKey)

        mutex.withLock {
            val routing = routing(channel.channelId!!, routingKey)

            val connections = routingTable[routing]

            connectionsTable[connectionId] = routing
            connectionMetadata[connectionId] = RoutingTarget(channelName, routingKey)

            if (connections == null) {
                routingTable[routing] = mutableListOf(connectionId)
                return
            }

            connections.firstOrNull { it == connectionId } ?: routingTable[routing]!!.add(connectionId)
        }
    }

    suspend fun remove(connectionId: String): RoutingTarget? =
        mutex.withLock {
            val routing = connectionsTable[connectionId]
            if (routing == null) {
                logger.info { "Connection not found in connections table: $connectionId" }
                return@withLock null
            }

            val connections = routingTable[routing]
            if (connections == null) {
                logger.info { "Connection not found routing table: $routing" }
                return@withLock null
            }

            routingTable[routing]!!.removeIf { it == connectionId }
            connectionsTable.remove(connectionId)
            val metadata = connectionMetadata.remove(connectionId)
            logger.info { "Removed connection from routing table: $connectionId" }
            metadata
        }

    suspend fun getByRoutingKey(routing: String): List<String> = mutex.withLock { routingTable[routing] ?: emptyList() }
}
