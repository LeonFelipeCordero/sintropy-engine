package com.ph.sintropyengine.broker.resource

import com.ph.sintropyengine.broker.service.ConnectionRouter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.websockets.next.OnClose
import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.WebSocket
import io.quarkus.websockets.next.WebSocketConnection
import jakarta.inject.Inject

private val logger = KotlinLogging.logger {}

@WebSocket(path = "/ws/streaming/{channelName}/{routingKey}")
class MessageStreaming {

    @Inject
    private lateinit var connectionRouter: ConnectionRouter

    @OnOpen(broadcast = true)
    suspend fun onOpen(webSocketConnection: WebSocketConnection) {
        val channelName = webSocketConnection.pathParam("channelName")
        val routingKey = webSocketConnection.pathParam("routingKey")
        connectionRouter.add(webSocketConnection.id(), channelName, routingKey)

        logger.info { "Consumer streaming started for connection ${webSocketConnection.id()}" }
    }

    @OnClose
    suspend fun onClose(webSocketConnection: WebSocketConnection) {
        connectionRouter.remove(webSocketConnection.id())
        logger.info { "Consumer streaming stopped for connection ${webSocketConnection.id()}" }
    }
}
