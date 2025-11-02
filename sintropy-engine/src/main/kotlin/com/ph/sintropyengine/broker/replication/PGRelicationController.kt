package com.ph.sintropyengine.broker.replication

import com.fasterxml.jackson.databind.ObjectMapper
import com.ph.sintropyengine.broker.service.ConnectionRouter
import com.ph.sintropyengine.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val objectMapper: ObjectMapper
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

                val connections = connectionRouter.getByRoutingKey(message.routing())

                // TODO: Create it's out response and catch global exemption to keep coroutine running
                openConnections
                    .filter { connections.contains(it.id()) }
                    .forEach {
                        val messageString = objectMapper.writeValueAsString(message)
                        it.sendText(messageString).awaitSuspending()
                        logger.info { "message sent ${message.routing()}: $message" }
                    }
            }
        }
    }
}
