package com.ph.sintropyengine.broker.consumption.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.ph.sintropyengine.broker.consumption.service.BatchStreamInput
import com.ph.sintropyengine.broker.consumption.service.MessageRecoveryService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.websockets.next.OnClose
import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.OnTextMessage
import io.quarkus.websockets.next.WebSocket
import io.quarkus.websockets.next.WebSocketConnection
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

private const val IDLE_TIMEOUT_SECONDS = 30L

@Path("/recovery")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class MessageRecoveryRestApi(
    private val messageRecoveryService: MessageRecoveryService,
) {
    @POST
    @Path("/messages/{messageId}/retrigger")
    fun retriggerMessage(
        @PathParam("messageId") messageId: UUID,
    ): Response {
        val messageLog = messageRecoveryService.retriggerMessage(messageId)
        return Response.ok(messageLog).build()
    }
}

@WebSocket(path = "/ws/recovery/{channelName}/{routingKey}")
class MessageRecoveryApi {
    @Inject
    private lateinit var messageRecoveryService: MessageRecoveryService

    @Inject
    private lateinit var objectMapper: ObjectMapper

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val idleTimeoutTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()

    @OnOpen
    fun onOpen(connection: WebSocketConnection): ReadyToStreamResponse {
        val channelName = connection.pathParam("channelName")
        val routingKey = connection.pathParam("routingKey")

        logger.info { "Recovery connection opened for $channelName/$routingKey - connectionId: ${connection.id()}" }

        scheduleIdleTimeout(connection)

        return ReadyToStreamResponse(
            connectionId = connection.id(),
            channelName = channelName,
            routingKey = routingKey,
            message = "Ready to stream. Send recovery request within $IDLE_TIMEOUT_SECONDS seconds.",
        )
    }

    @OnTextMessage
    fun onMessage(
        connection: WebSocketConnection,
        message: String,
    ) {
        val channelName = connection.pathParam("channelName")
        val routingKey = connection.pathParam("routingKey")

        logger.info { "Received recovery request for $channelName/$routingKey: $message" }

        cancelIdleTimeout(connection.id())

        try {
            val request = objectMapper.readValue(message, RecoveryStreamRequest::class.java)

            val batchStreamInput =
                BatchStreamInput(
                    connectionId = connection.id(),
                    batchSize = request.batchSize,
                    delayInMs = request.delayInMs,
                )

            if (request.streamAll) {
                messageRecoveryService.streamFromAll(
                    channelName = channelName,
                    routingKey = routingKey,
                    batchStreamInput = batchStreamInput,
                )
            } else {
                messageRecoveryService.streamFromToByChannelIdAndRoutingKey(
                    channelName = channelName,
                    routingKey = routingKey,
                    from =
                        request.from
                            ?: throw kotlin.IllegalStateException("Provide an start point when not requesting a full recovery"),
                    to = request.to,
                    batchStreamInput = batchStreamInput,
                )
            }

            connection.sendTextAndAwait(
                objectMapper.writeValueAsString(
                    StreamingCompleteResponse(message = "Streaming complete"),
                ),
            )
        } catch (e: Exception) {
            logger.error(e) { "Error processing recovery request for $channelName/$routingKey" }
            connection.sendTextAndAwait(
                objectMapper.writeValueAsString(
                    StreamingErrorResponse(error = e.message ?: "Unknown error"),
                ),
            )
        }
    }

    @OnClose
    fun onClose(connection: WebSocketConnection) {
        cancelIdleTimeout(connection.id())
        logger.info { "Recovery connection closed - connectionId: ${connection.id()}" }
    }

    private fun scheduleIdleTimeout(connection: WebSocketConnection) {
        val task =
            scheduler.schedule({
                logger.warn { "Idle timeout reached for connection ${connection.id()}, closing connection" }
                connection.sendTextAndAwait(
                    objectMapper.writeValueAsString(
                        StreamingErrorResponse(error = "Connection closed due to idle timeout ($IDLE_TIMEOUT_SECONDS seconds)"),
                    ),
                )
                connection.close().await().indefinitely()
            }, IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        idleTimeoutTasks[connection.id()] = task
    }

    private fun cancelIdleTimeout(connectionId: String) {
        idleTimeoutTasks.remove(connectionId)?.cancel(false)
    }
}

data class ReadyToStreamResponse(
    val connectionId: String,
    val channelName: String,
    val routingKey: String,
    val message: String,
)

data class RecoveryStreamRequest(
    val batchSize: Int = 500,
    val delayInMs: Int = 100,
    val from: OffsetDateTime? = null,
    val to: OffsetDateTime? = null,
    val streamAll: Boolean = false,
)

data class StreamingCompleteResponse(
    val message: String,
)

data class StreamingErrorResponse(
    val error: String,
)
