package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.model.ChannelCircuitBreaker
import com.ph.sintropyengine.broker.consumption.model.CircuitState
import com.ph.sintropyengine.broker.consumption.repository.CircuitBreakerRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class CircuitBreakerService(
    private val circuitBreakerRepository: CircuitBreakerRepository,
    private val channelService: ChannelService,
    private val deadLetterQueueService: DeadLetterQueueService,
) {
    fun getCircuitState(
        channelName: String,
        routingKey: String,
    ): CircuitState {
        val channel = channelService.findByNameAndRoutingKeyStrict(channelName, routingKey)
        return circuitBreakerRepository.getCircuitState(channel.channelId!!, routingKey)
    }

    fun getCircuitBreaker(
        channelName: String,
        routingKey: String,
    ): ChannelCircuitBreaker? {
        val channel = channelService.findByNameAndRoutingKeyStrict(channelName, routingKey)
        return circuitBreakerRepository.findByChannelIdAndRoutingKey(channel.channelId!!, routingKey)
    }

    fun getAllOpenCircuits(): List<ChannelCircuitBreaker> = circuitBreakerRepository.findAllOpen()

    fun getCircuitBreakersForChannel(channelName: String): List<ChannelCircuitBreaker> {
        val channel =
            channelService.findByName(channelName)
                ?: throw IllegalStateException("Channel with name $channelName not found")

        return circuitBreakerRepository.findAllByChannelId(channel.channelId!!)
    }

    @Transactional
    fun closeCircuit(
        channelName: String,
        routingKey: String,
    ) {
        val channel = channelService.findByNameAndRoutingKeyStrict(channelName, routingKey)

        val currentState = circuitBreakerRepository.getCircuitState(channel.channelId!!, routingKey)
        if (currentState == CircuitState.CLOSED) {
            logger.info { "Circuit for $channelName/$routingKey is already closed" }
            return
        }

        circuitBreakerRepository.closeCircuit(channel.channelId, routingKey)
        logger.info { "Closed circuit for $channelName/$routingKey" }
    }

    @Transactional
    fun closeCircuitAndRecover(
        channelName: String,
        routingKey: String,
    ): Int {
        val channel = channelService.findByNameAndRoutingKeyStrict(channelName, routingKey)

        val currentState = circuitBreakerRepository.getCircuitState(channel.channelId!!, routingKey)
        if (currentState == CircuitState.CLOSED) {
            logger.info { "Circuit for $channelName/$routingKey is already closed" }
            return 0
        }

        // Close the circuit first
        circuitBreakerRepository.closeCircuit(channel.channelId, routingKey)

        // Recover messages from DLQ
        val recoveredMessages = deadLetterQueueService.recoverAllForChannelAndRoutingKey(channelName, routingKey)
        logger.info { "Closed circuit for $channelName/$routingKey and recovered ${recoveredMessages.size} messages" }

        return recoveredMessages.size
    }
}
