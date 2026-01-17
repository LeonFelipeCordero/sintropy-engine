package com.ph.sintropyengine.broker.channel.repository

import com.ph.sintropyengine.broker.channel.model.Channel
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.model.RoutingKeyCircuitState
import com.ph.sintropyengine.broker.consumption.model.CircuitState
import com.ph.sintropyengine.jooq.generated.Tables.CHANNELS
import com.ph.sintropyengine.jooq.generated.Tables.CHANNEL_CIRCUIT_BREAKERS
import com.ph.sintropyengine.jooq.generated.Tables.QUEUES
import com.ph.sintropyengine.jooq.generated.Tables.ROUTING_KEYS
import jakarta.enterprise.context.ApplicationScoped
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Result
import java.util.UUID

@ApplicationScoped
class ChannelRepository(
    private var context: DSLContext,
) {
    fun save(channel: Channel): Channel {
        val channelRecord =
            context
                .insertInto(
                    CHANNELS,
                    CHANNELS.NAME,
                    CHANNELS.CHANNEL_TYPE,
                ).values(channel.name, channel.channelType.toDBEnum())
                .returning()
                .fetchOne()

        // TODO: ths can be solved with context.batch([...items])
        var routingKeysInsertStatement =
            context.insertInto(ROUTING_KEYS, ROUTING_KEYS.ROUTING_KEY, ROUTING_KEYS.CHANNEL_ID)
        channel.routingKeys
            .forEach {
                routingKeysInsertStatement = routingKeysInsertStatement.values(it, channelRecord?.channelId)
            }
        val routingKeyRecords = routingKeysInsertStatement.returning().fetch()

        val queueRecord =
            if (channel.channelType == QUEUE) {
                context
                    .insertInto(QUEUES, QUEUES.CHANNEL_ID, QUEUES.CONSUMPTION_TYPE)
                    .values(
                        channelRecord?.channelId,
                        channel.consumptionType?.toDBEnum()
                            ?: throw IllegalStateException("Channel type is QUEUE, but not consumption type is provided"),
                    ).returning()
                    .fetchOne()
            } else {
                null
            }

        return Channel(
            channelId = channelRecord!!.channelId,
            name = channelRecord.name,
            channelType = channelRecord.channelType.toDomainEnum(),
            routingKeys = routingKeyRecords.map { it.routingKey },
            consumptionType = queueRecord?.consumptionType?.toDomainEnum(),
        )
    }

    fun findById(id: UUID): Channel? {
        val records =
            context
                .select(
                    CHANNELS.asterisk(),
                    ROUTING_KEYS.ROUTING_KEY,
                    QUEUES.CONSUMPTION_TYPE,
                    CHANNEL_CIRCUIT_BREAKERS.STATE,
                ).from(CHANNELS)
                .leftJoin(ROUTING_KEYS)
                .on(CHANNELS.CHANNEL_ID.eq(ROUTING_KEYS.CHANNEL_ID))
                .leftJoin(QUEUES)
                .on(CHANNELS.CHANNEL_ID.eq(QUEUES.CHANNEL_ID))
                .leftJoin(CHANNEL_CIRCUIT_BREAKERS)
                .on(CHANNELS.CHANNEL_ID.eq(CHANNELS.CHANNEL_ID))
                .where(CHANNELS.CHANNEL_ID.eq(id))
                .groupBy(
                    CHANNELS.CHANNEL_ID,
                    ROUTING_KEYS.ROUTING_KEY,
                    QUEUES.CONSUMPTION_TYPE,
                    CHANNEL_CIRCUIT_BREAKERS.STATE,
                ).fetch()

        return mapRecordsWithKeysToDTO(records).firstOrNull()
    }

    fun findByName(name: String): Channel? {
        val records =
            context
                .select(
                    CHANNELS.asterisk(),
                    ROUTING_KEYS.ROUTING_KEY,
                    QUEUES.CONSUMPTION_TYPE,
                    CHANNEL_CIRCUIT_BREAKERS.STATE,
                ).from(CHANNELS)
                .leftJoin(ROUTING_KEYS)
                .on(CHANNELS.CHANNEL_ID.eq(ROUTING_KEYS.CHANNEL_ID))
                .leftJoin(QUEUES)
                .on(CHANNELS.CHANNEL_ID.eq(QUEUES.CHANNEL_ID))
                .leftJoin(CHANNEL_CIRCUIT_BREAKERS)
                .on(CHANNELS.CHANNEL_ID.eq(CHANNELS.CHANNEL_ID))
                .where(CHANNELS.NAME.eq(name))
                .groupBy(
                    CHANNELS.CHANNEL_ID,
                    ROUTING_KEYS.ROUTING_KEY,
                    QUEUES.CONSUMPTION_TYPE,
                    CHANNEL_CIRCUIT_BREAKERS.STATE,
                ).fetch()

        return mapRecordsWithKeysToDTO(records).firstOrNull()
    }

    fun findByNameAndRoutingKey(
        name: String,
        routingKey: String,
    ): Channel? {
        val records =
            context
                .select(
                    CHANNELS.asterisk(),
                    ROUTING_KEYS.ROUTING_KEY,
                    QUEUES.CONSUMPTION_TYPE,
                    CHANNEL_CIRCUIT_BREAKERS.STATE,
                ).from(CHANNELS)
                .leftJoin(ROUTING_KEYS)
                .on(CHANNELS.CHANNEL_ID.eq(ROUTING_KEYS.CHANNEL_ID))
                .leftJoin(QUEUES)
                .on(CHANNELS.CHANNEL_ID.eq(QUEUES.CHANNEL_ID))
                .leftJoin(CHANNEL_CIRCUIT_BREAKERS)
                .on(CHANNELS.CHANNEL_ID.eq(CHANNELS.CHANNEL_ID))
                .where(CHANNELS.NAME.eq(name))
                .and(ROUTING_KEYS.ROUTING_KEY.eq(routingKey))
                .groupBy(
                    CHANNELS.CHANNEL_ID,
                    ROUTING_KEYS.ROUTING_KEY,
                    QUEUES.CONSUMPTION_TYPE,
                    CHANNEL_CIRCUIT_BREAKERS.STATE,
                ).fetch()

        return mapRecordsWithKeysToDTO(records).firstOrNull()
    }

    fun delete(channelId: UUID) {
        context
            .delete(ROUTING_KEYS)
            .where(ROUTING_KEYS.CHANNEL_ID.eq(channelId))
            .execute()

        context
            .deleteFrom(QUEUES)
            .where(QUEUES.CHANNEL_ID.eq(channelId))
            .execute()

        context
            .delete(CHANNELS)
            .where(CHANNELS.CHANNEL_ID.eq(channelId))
            .execute()
    }

    fun addRoutingKey(
        channelId: UUID,
        routingKey: String,
    ) {
        context
            .insertInto(ROUTING_KEYS, ROUTING_KEYS.ROUTING_KEY, ROUTING_KEYS.CHANNEL_ID)
            .values(routingKey, channelId)
            .execute()
    }

    fun findAll(): List<Channel> {
        val records =
            context
                .select(
                    CHANNELS.asterisk(),
                    ROUTING_KEYS.ROUTING_KEY,
                    QUEUES.CONSUMPTION_TYPE,
                    CHANNEL_CIRCUIT_BREAKERS.STATE,
                ).from(CHANNELS)
                .leftJoin(ROUTING_KEYS)
                .on(CHANNELS.CHANNEL_ID.eq(ROUTING_KEYS.CHANNEL_ID))
                .leftJoin(QUEUES)
                .on(CHANNELS.CHANNEL_ID.eq(QUEUES.CHANNEL_ID))
                .leftJoin(CHANNEL_CIRCUIT_BREAKERS)
                .on(CHANNELS.CHANNEL_ID.eq(CHANNELS.CHANNEL_ID))
                .groupBy(
                    CHANNELS.CHANNEL_ID,
                    ROUTING_KEYS.ROUTING_KEY,
                    QUEUES.CONSUMPTION_TYPE,
                    CHANNEL_CIRCUIT_BREAKERS.STATE,
                ).fetch()

        return mapRecordsWithKeysToDTO(records)
    }

    fun deleteAll() {
        context.deleteFrom(ROUTING_KEYS).execute()
        context.deleteFrom(QUEUES).execute()
        context.deleteFrom(CHANNELS).execute()
    }

    private fun mapRecordsWithKeysToDTO(records: Result<Record>): List<Channel> =
        records
            .groupBy { record ->
                Triple(record[CHANNELS.CHANNEL_ID], record[CHANNELS.NAME], record[CHANNELS.CHANNEL_TYPE])
            }.map { (key, rows) ->
                Channel(
                    channelId = key.first,
                    name = key.second,
                    channelType = key.third.toDomainEnum(),
                    routingKeys = rows.map { it[ROUTING_KEYS.ROUTING_KEY] }.toMutableList(),
                    consumptionType =
                        if (key.third.toDomainEnum() == QUEUE) {
                            rows.map { it[QUEUES.CONSUMPTION_TYPE] }.first().toDomainEnum()
                        } else {
                            null
                        },
                    routingKeysCircuitState =
                        rows.map {
                            RoutingKeyCircuitState(
                                it[CHANNEL_CIRCUIT_BREAKERS.ROUTING_KEY],
                                it[CHANNEL_CIRCUIT_BREAKERS.STATE].toDomainEnum(),
                            )
                        },
                )
            }
}

private fun ConsumptionType.toDBEnum(): com.ph.sintropyengine.jooq.generated.enums.ConsumptionType =
    com.ph.sintropyengine.jooq.generated.enums.ConsumptionType
        .valueOf(this.toString())

private fun com.ph.sintropyengine.jooq.generated.enums.ConsumptionType.toDomainEnum(): ConsumptionType =
    ConsumptionType.valueOf(this.toString())

private fun ChannelType.toDBEnum(): com.ph.sintropyengine.jooq.generated.enums.ChannelType =
    com.ph.sintropyengine.jooq.generated.enums.ChannelType
        .valueOf(this.toString())

private fun com.ph.sintropyengine.jooq.generated.enums.ChannelType.toDomainEnum(): ChannelType = ChannelType.valueOf(this.toString())

private fun com.ph.sintropyengine.jooq.generated.enums.CircuitState.toDomainEnum(): CircuitState = CircuitState.valueOf(this.toString())
