package com.ph.sintropyengine.broker.replication

import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

@Startup
@ApplicationScoped
class PGReplicationController(
    private val pgReplicationConsumerFactory: PGReplicationConsumerFactory
) {

    private final val job = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + job)

    @PostConstruct
    fun onAppReady() {
        logger.info { "Starting the PSQL replication stream consumption..." }

        val replicationConsumer = pgReplicationConsumerFactory.getStreamConsumer()
        scope.launch { replicationConsumer.startConsuming() }

        scope.launch {
            replicationConsumer.channel().consumeEach { message ->
                logger.debug { "### new message: $message" }
            }
        }
    }


}
