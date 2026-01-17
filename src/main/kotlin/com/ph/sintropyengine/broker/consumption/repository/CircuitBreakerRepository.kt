package com.ph.sintropyengine.broker.consumption.repository

import com.ph.sintropyengine.broker.consumption.model.ChannelCircuitBreaker
import com.ph.sintropyengine.broker.consumption.model.CircuitState
import com.ph.sintropyengine.jooq.generated.Tables
import jakarta.enterprise.context.ApplicationScoped
import org.jooq.DSLContext
import java.time.OffsetDateTime
import java.util.UUID
import com.ph.sintropyengine.jooq.generated.enums.CircuitState as JooqCircuitState

@ApplicationScoped
class CircuitBreakerRepository(
    private val context: DSLContext,
) {
    fun findByChannelIdAndRoutingKey(
        channelId: UUID,
        routingKey: String,
    ): ChannelCircuitBreaker? =
        context
            .selectFrom(Tables.CHANNEL_CIRCUIT_BREAKERS)
            .where(Tables.CHANNEL_CIRCUIT_BREAKERS.CHANNEL_ID.eq(channelId))
            .and(Tables.CHANNEL_CIRCUIT_BREAKERS.ROUTING_KEY.eq(routingKey))
            .fetchOneInto(ChannelCircuitBreaker::class.java)

    fun findAllOpen(): List<ChannelCircuitBreaker> =
        context
            .selectFrom(Tables.CHANNEL_CIRCUIT_BREAKERS)
            .where(Tables.CHANNEL_CIRCUIT_BREAKERS.STATE.eq(JooqCircuitState.OPEN))
            .fetchInto(ChannelCircuitBreaker::class.java)

    fun findAllByChannelId(channelId: UUID): List<ChannelCircuitBreaker> =
        context
            .selectFrom(Tables.CHANNEL_CIRCUIT_BREAKERS)
            .where(Tables.CHANNEL_CIRCUIT_BREAKERS.CHANNEL_ID.eq(channelId))
            .fetchInto(ChannelCircuitBreaker::class.java)

    fun getCircuitState(
        channelId: UUID,
        routingKey: String,
    ): CircuitState {
        val state =
            context
                .select(Tables.CHANNEL_CIRCUIT_BREAKERS.STATE)
                .from(Tables.CHANNEL_CIRCUIT_BREAKERS)
                .where(Tables.CHANNEL_CIRCUIT_BREAKERS.CHANNEL_ID.eq(channelId))
                .and(Tables.CHANNEL_CIRCUIT_BREAKERS.ROUTING_KEY.eq(routingKey))
                .fetchOneInto(JooqCircuitState::class.java)

        return CircuitState.valueOf(state?.name ?: "CLOSED")
    }

    fun closeCircuit(
        channelId: UUID,
        routingKey: String,
    ) {
        context
            .update(Tables.CHANNEL_CIRCUIT_BREAKERS)
            .set(Tables.CHANNEL_CIRCUIT_BREAKERS.STATE, JooqCircuitState.CLOSED)
            .set(Tables.CHANNEL_CIRCUIT_BREAKERS.UPDATED_AT, OffsetDateTime.now())
            .where(Tables.CHANNEL_CIRCUIT_BREAKERS.CHANNEL_ID.eq(channelId))
            .and(Tables.CHANNEL_CIRCUIT_BREAKERS.ROUTING_KEY.eq(routingKey))
            .execute()
    }

    fun findAll(): List<ChannelCircuitBreaker> =
        context
            .selectFrom(Tables.CHANNEL_CIRCUIT_BREAKERS)
            .fetchInto(ChannelCircuitBreaker::class.java)

    fun deleteAll() {
        context.deleteFrom(Tables.CHANNEL_CIRCUIT_BREAKERS).execute()
    }
}
