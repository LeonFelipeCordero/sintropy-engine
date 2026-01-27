package com.ph.sintropyengine.broker.shared.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

@ApplicationScoped
class ObservabilityService(
    private val registry: MeterRegistry,
) {
    private val openCircuitsGauge = AtomicInteger(0)
    private val counterCache = ConcurrentHashMap<String, Counter>()
    private val timerCache = ConcurrentHashMap<String, Timer>()

    init {
        registry.gauge("sintropy.circuit_breaker.open.count", openCircuitsGauge)
    }

    private fun getOrCreateCounter(
        name: String,
        description: String,
        tags: Map<String, String>,
    ): Counter {
        val cacheKey = "$name:${tags.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }}"
        return counterCache.computeIfAbsent(cacheKey) {
            val builder = Counter.builder(name).description(description)
            tags.forEach { (key, value) -> builder.tag(key, value) }
            builder.register(registry)
        }
    }

    fun recordMessagePublished(
        channelName: String,
        routingKey: String,
        producerName: String,
    ) {
        getOrCreateCounter(
            "sintropy.messages.published.total",
            "Total number of messages published",
            mapOf("channel" to channelName, "routing_key" to routingKey, "producer" to producerName),
        ).increment()
    }

    fun recordMessageStreamed(
        channelName: String,
        routingKey: String,
    ) {
        getOrCreateCounter(
            "sintropy.messages.streamed.total",
            "Total number of messages streamed via WebSocket",
            mapOf("channel" to channelName, "routing_key" to routingKey),
        ).increment()
    }

    fun recordMessagesPolled(
        channelName: String,
        routingKey: String,
        consumptionType: String,
        count: Int,
    ) {
        getOrCreateCounter(
            "sintropy.messages.polled.total",
            "Total number of messages polled from queues",
            mapOf("channel" to channelName, "routing_key" to routingKey, "consumption_type" to consumptionType),
        ).increment(count.toDouble())
    }

    fun recordMessageDequeued(
        channelName: String,
        routingKey: String,
    ) {
        getOrCreateCounter(
            "sintropy.messages.dequeued.total",
            "Total number of messages successfully dequeued",
            mapOf("channel" to channelName, "routing_key" to routingKey),
        ).increment()
    }

    fun recordMessageFailed(
        channelName: String,
        routingKey: String,
    ) {
        getOrCreateCounter(
            "sintropy.messages.failed.total",
            "Total number of messages marked as failed",
            mapOf("channel" to channelName, "routing_key" to routingKey),
        ).increment()
    }

    fun recordCircuitClosed(
        channelName: String,
        routingKey: String,
    ) {
        getOrCreateCounter(
            "sintropy.circuit_breaker.closed.total",
            "Total number of times circuit breakers have been closed",
            mapOf("channel" to channelName, "routing_key" to routingKey),
        ).increment()
        openCircuitsGauge.decrementAndGet()
    }

    fun setOpenCircuitsCount(count: Int) {
        openCircuitsGauge.set(count)
    }

    fun recordMessageRecoveredFromDlq(
        channelName: String,
        routingKey: String,
        count: Int = 1,
    ) {
        getOrCreateCounter(
            "sintropy.dlq.messages.recovered.total",
            "Total number of messages recovered from the dead letter queue",
            mapOf("channel" to channelName, "routing_key" to routingKey),
        ).increment(count.toDouble())
    }

    fun recordWebSocketConnected(
        channelName: String,
        routingKey: String,
    ) {
        getOrCreateCounter(
            "sintropy.websocket.connections.total",
            "Total number of WebSocket connections established",
            mapOf("channel" to channelName, "routing_key" to routingKey),
        ).increment()
    }

    fun recordWebSocketDisconnected(
        channelName: String,
        routingKey: String,
    ) {
        getOrCreateCounter(
            "sintropy.websocket.disconnections.total",
            "Total number of WebSocket disconnections",
            mapOf("channel" to channelName, "routing_key" to routingKey),
        ).increment()
    }
}
