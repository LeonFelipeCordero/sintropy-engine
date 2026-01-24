package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.model.ChannelCircuitBreaker
import com.ph.sintropyengine.broker.consumption.model.CircuitState
import com.ph.sintropyengine.broker.consumption.repository.CircuitBreakerRepository
import com.ph.sintropyengine.broker.shared.observability.ObservabilityService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.runtime.Startup
import io.quarkus.scheduler.Scheduled
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

private val logger = KotlinLogging.logger {}

@Startup
@ApplicationScoped
class CircuitBreakerService(
    private val circuitBreakerRepository: CircuitBreakerRepository,
    private val channelService: ChannelService,
    private val deadLetterQueueService: DeadLetterQueueService,
    private val observabilityService: ObservabilityService,
) {
    @PostConstruct
    fun initializeMetrics() {
        val openCircuits = circuitBreakerRepository.findAllOpen()
        observabilityService.setOpenCircuitsCount(openCircuits.size)
        logger.info { "Initialized circuit breaker metrics: ${openCircuits.size} circuits currently open" }
    }

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
            logger.debug { "Circuit already closed [channel=$channelName, routingKey=$routingKey]" }
            return
        }

        circuitBreakerRepository.closeCircuit(channel.channelId, routingKey)
        observabilityService.recordCircuitClosed(channelName, routingKey)

        logger.info { "Circuit breaker closed [channel=$channelName, routingKey=$routingKey]" }
    }

    @Transactional
    fun closeCircuitAndRecover(
        channelName: String,
        routingKey: String,
    ): Int {
        val channel = channelService.findByNameAndRoutingKeyStrict(channelName, routingKey)

        val currentState = circuitBreakerRepository.getCircuitState(channel.channelId!!, routingKey)
        if (currentState == CircuitState.CLOSED) {
            logger.debug { "Circuit already closed [channel=$channelName, routingKey=$routingKey]" }
            return 0
        }

        circuitBreakerRepository.closeCircuit(channel.channelId, routingKey)
        observabilityService.recordCircuitClosed(channelName, routingKey)

        val recoveredMessages = deadLetterQueueService.recoverAllForChannelAndRoutingKey(channelName, routingKey)

        logger.info {
            "Circuit breaker closed with recovery [channel=$channelName, routingKey=$routingKey, " +
                "recoveredMessages=${recoveredMessages.size}]"
        }

        return recoveredMessages.size
    }

    @Scheduled(every = "30s")
    fun syncOpenCircuitsGauge() {
        val openCircuits = circuitBreakerRepository.findAllOpen()
        val currentCount = openCircuits.size
        observabilityService.setOpenCircuitsCount(currentCount)
        if (currentCount > 0) {
            logger.debug { "Circuit breaker gauge synced: $currentCount circuits open" }
        }
    }
}
