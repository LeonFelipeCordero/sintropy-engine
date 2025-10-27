package com.ph.syntropyengine.broker.replication

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class PGReplicationController(
    private val pgReplicationConsumerFactory: PGReplicationConsumerFactory
) {
    private final val job = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + job)

    @EventListener(ApplicationReadyEvent::class)
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
