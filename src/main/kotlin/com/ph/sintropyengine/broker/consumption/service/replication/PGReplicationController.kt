package com.ph.sintropyengine.broker.consumption.service.replication

import com.fasterxml.jackson.databind.ObjectMapper
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.api.response.toResponse
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.consumption.service.ConnectionRouter
import com.ph.sintropyengine.broker.producer.service.ProducerService
import com.ph.sintropyengine.broker.shared.observability.ObservabilityService
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.runtime.Startup
import io.quarkus.websockets.next.OpenConnections
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

@Startup
@ApplicationScoped
class PGReplicationController(
    private val pgReplicationConsumerFactory: PGReplicationConsumerFactory,
    private val connectionRouter: ConnectionRouter,
    private val objectMapper: ObjectMapper,
    private val messageRepository: MessageRepository,
    private val channelService: ChannelService,
    private val producerService: ProducerService,
    private val observabilityService: ObservabilityService,
) {
    @Inject
    private lateinit var openConnections: OpenConnections

    private final val job = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + job)

    @PostConstruct
    fun onAppReady() {
        logger.info { "Starting the PSQL replication stream consumption..." }

        val replicationConsumer = pgReplicationConsumerFactory.getStreamConsumer()
        scope.launch { replicationConsumer.startConsuming() }

        scope.launch {
            while (true) {
                val message = replicationConsumer.channel().receive()
                try {
                    // TODO get a method that fetch channel and producer together
                    val channel =
                        channelService.findById(message.channelId)
                            ?: throw IllegalStateException("No channel found for ${message.channelId}")

                    if (channel.channelType == ChannelType.QUEUE) {
                        logger.debug { "Skipping message ${message.messageId} because channel is for queue polling" }
                        continue
                    }

                    val connections = connectionRouter.getByRoutingKey(message.routing())

                    val producer =
                        producerService.findById(message.producerId)
                            ?: throw IllegalStateException("No producer found for ${message.producerId}")

                    val messageResponse = message.toResponse(channel.name, producer.name)

                    val connectedClients = openConnections.filter { connections.contains(it.id()) }
                    val clientCount = connectedClients.count()

                    connectedClients.forEach {
                        val messageString = objectMapper.writeValueAsString(messageResponse)
                        it.sendText(messageString).awaitSuspending()
                        observabilityService.recordMessageStreamed(channel.name, message.routingKey)
                    }

                    logger.info {
                        "Streamed message ${message.messageId} to $clientCount clients " +
                            "[channel=${channel.name}, routingKey=${message.routingKey}, producer=${producer.name}]"
                    }

                    launch {
                        QuarkusTransaction.requiringNew().run {
                            messageRepository.dequeue(message.messageId)
                            logger.debug { "Dequeued streamed message ${message.messageId} from ${message.routing()}" }
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) {
                        "Failed to process message from WAL [messageId=${message.messageId}, " +
                            "channelId=${message.channelId}, routingKey=${message.routingKey}]"
                    }
                }
            }
        }
    }
}
