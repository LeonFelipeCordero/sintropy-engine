package com.ph.sintropyengine.broker.consumption.service.replication

import com.fasterxml.jackson.databind.ObjectMapper
import com.ph.sintropyengine.broker.chennel.model.ChannelType
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.chennel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.service.ConnectionRouter
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
    private val channelService: ChannelService
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

                val channel = channelService.findById(message.channelId)
                    ?: throw IllegalStateException("No channel found for ${message.channelId}")

                if (channel.channelType == ChannelType.QUEUE) {
                    logger.debug { "Skipping message ${message.messageId} because channel is for queue polling" }
                    continue
                }

                val connections = connectionRouter.getByRoutingKey(message.routing())

                // TODO: Create it's out response and catch global exemption to keep coroutine running
                // TODO: Different object to stream back
                openConnections
                    .filter { connections.contains(it.id()) }
                    .forEach {
                        val messageString = objectMapper.writeValueAsString(message)
                        it.sendText(messageString).awaitSuspending()
                        logger.debug { "message sent ${message.routing()}: $message" }
                    }

                launch {
                    QuarkusTransaction.requiringNew().run {
                        messageRepository.dequeue(message.messageId)
                        logger.debug { "Removed message ${message.routing()}: $message" }
                    }
                }
            }
        }
    }
}
