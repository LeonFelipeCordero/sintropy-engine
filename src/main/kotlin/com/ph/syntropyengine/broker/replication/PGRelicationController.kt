package com.ph.syntropyengine.broker.replication

import com.ph.syntropyengine.configuration.DatabaseProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
// TODO move this down the line to make a real and fake implementation base on the flag
@ConditionalOnProperty(value = ["syen.feature-flags.with-full-replication"], havingValue = "true")
class PGReplicationController(
    private val databaseProperties: DatabaseProperties
) {
    private final val job = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + job)


    @EventListener(ApplicationReadyEvent::class)
    fun onAppReady() {
        logger.info { "Starting the PSQL replication stream consumption..." }

        val replicationConsumer = PGReplicationConsumer.connect(databaseProperties)
        scope.launch { replicationConsumer.startConsuming() }

        scope.launch {
            replicationConsumer.channel.consumeEach { message ->
                logger.info { "### new message: $message" }

                /**
                 * Take the incoming message and give to the message routes
                 * The message router takes care of
                 * 1) Find the consumer - could be one channel and one coroutine per consumer
                 */
            }
        }
    }
}
