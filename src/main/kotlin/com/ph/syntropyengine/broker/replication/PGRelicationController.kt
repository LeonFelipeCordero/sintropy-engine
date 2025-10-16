package com.ph.syntropyengine.broker.replication

import com.ph.syntropyengine.broker.service.MessageRouter
import com.ph.syntropyengine.broker.service.PollingStandardQueue
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
    private val databaseProperties: DatabaseProperties,
    private val messageRouter: MessageRouter,
    private val pollingQueue: PollingStandardQueue
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
                logger.debug { "### new message: $message" }

//                val consumers = messageRouter.getConsumersByRouting(message.channelId, message.routingKey)
//
//                consumers.forEach { consumer ->
//
//                }
            }
        }
    }
}
