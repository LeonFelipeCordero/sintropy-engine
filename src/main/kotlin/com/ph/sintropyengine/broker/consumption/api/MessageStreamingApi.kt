package com.ph.sintropyengine.broker.consumption.api

import com.ph.sintropyengine.broker.consumption.service.ConnectionRouter
import com.ph.sintropyengine.broker.shared.observability.ObservabilityService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.websockets.next.OnClose
import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.WebSocket
import io.quarkus.websockets.next.WebSocketConnection
import jakarta.inject.Inject

private val logger = KotlinLogging.logger {}

@WebSocket(path = "/ws/streaming/{channelName}/{routingKey}")
class MessageStreamingApi {
    @Inject
    private lateinit var connectionRouter: ConnectionRouter

    @Inject
    private lateinit var observabilityService: ObservabilityService

    @OnOpen(broadcast = true)
    suspend fun onOpen(webSocketConnection: WebSocketConnection) {
        val channelName = webSocketConnection.pathParam("channelName")
        val routingKey = webSocketConnection.pathParam("routingKey")
        connectionRouter.add(webSocketConnection.id(), channelName, routingKey)
        observabilityService.recordWebSocketConnected(channelName, routingKey)

        logger.info {
            "WebSocket connection established [connectionId=${webSocketConnection.id()}, " +
                "channel=$channelName, routingKey=$routingKey]"
        }
    }

    @OnClose
    suspend fun onClose(webSocketConnection: WebSocketConnection) {
        logger.info { "Closing connection [connectionId=${webSocketConnection.id()}]" }

        val routingTarget = connectionRouter.remove(webSocketConnection.id())
        routingTarget?.let {
            observabilityService.recordWebSocketDisconnected(it.channelName, it.routingKey)
            logger.info {
                "WebSocket connection closed [connectionId=${webSocketConnection.id()}, " +
                    "channel=${it.channelName}, routingKey=${it.routingKey}]"
            }
        } ?: logger.warn { "WebSocket connection closed but no routing target found [connectionId=${webSocketConnection.id()}]" }
    }
}
