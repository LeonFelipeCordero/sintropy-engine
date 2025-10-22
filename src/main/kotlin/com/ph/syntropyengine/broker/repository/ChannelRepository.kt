package com.ph.syntropyengine.broker.repository

import com.ph.syntropyengine.broker.model.Channel
import com.ph.syntropyengine.broker.model.ChannelType
import com.ph.syntropyengine.jooq.generated.Tables.CHANNELS
import com.ph.syntropyengine.jooq.generated.Tables.ROUTING_KEY
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Result
import org.springframework.stereotype.Repository

@Repository
class ChannelRepository(
    private var context: DSLContext,
) {

    fun save(channel: Channel): Channel {
        val channelId = UUID.randomUUID()
        val channelRecord = context.insertInto(CHANNELS, CHANNELS.CHANNEL_ID, CHANNELS.NAME, CHANNELS.CHANNEL_TYPE)
            .values(channelId, channel.name, channel.channelType.toDBEnum())
            .returning()
            .fetchOne()

        // TODO: ths can be solved with context.batch([...items])
        var routingKeysInsertStatement =
            context.insertInto(ROUTING_KEY, ROUTING_KEY.ROUTING_KEY_, ROUTING_KEY.CHANNEL_ID)
        channel.routingKeys
            .forEach {
                routingKeysInsertStatement = routingKeysInsertStatement.values(it, channelRecord?.channelId)
            }
        val routingKeyRecords = routingKeysInsertStatement.returning().fetch()

        return Channel(
            channelId = channelRecord!!.channelId,
            name = channelRecord.name,
            channelType = channelRecord.channelType.toDomainEnum(),
            routingKeys = routingKeyRecords.map { it.routingKey }
        )
    }

    fun findById(id: UUID): Channel? {
        val records = context.select(CHANNELS.asterisk(), ROUTING_KEY.ROUTING_KEY_)
            .from(CHANNELS)
            .leftJoin(ROUTING_KEY).on(CHANNELS.CHANNEL_ID.eq(ROUTING_KEY.CHANNEL_ID))
            .where(CHANNELS.CHANNEL_ID.eq(id))
            .fetch()

        return mapRecordWithKeysToDTO(records)
    }

    fun findByName(name: String): Channel? {
        val records = context.select(CHANNELS.asterisk(), ROUTING_KEY.ROUTING_KEY_)
            .from(CHANNELS)
            .leftJoin(ROUTING_KEY).on(CHANNELS.CHANNEL_ID.eq(ROUTING_KEY.CHANNEL_ID))
            .where(CHANNELS.NAME.eq(name))
            .fetch()

        return mapRecordWithKeysToDTO(records)
    }

    fun delete(channelId: UUID) {
        context.delete(ROUTING_KEY)
            .where(ROUTING_KEY.CHANNEL_ID.eq(channelId))
            .execute()

        context.delete(CHANNELS)
            .where(CHANNELS.CHANNEL_ID.eq(channelId))
            .execute()
    }

    fun addRoutingKey(channelId: UUID, routingKey: String) {
        context.insertInto(ROUTING_KEY, ROUTING_KEY.ROUTING_KEY_, ROUTING_KEY.CHANNEL_ID)
            .values(routingKey, channelId)
            .execute()
    }

    fun deleteAll() {
        context.deleteFrom(ROUTING_KEY).execute()
        context.deleteFrom(CHANNELS).execute()
    }

    private fun mapRecordWithKeysToDTO(records: Result<Record>): Channel? {
        return records.groupBy { record ->
            Triple(record[CHANNELS.CHANNEL_ID], record[CHANNELS.NAME], record[CHANNELS.CHANNEL_TYPE])
        }.map { (key, rows) ->
            Channel(
                channelId = key.first,
                name = key.second,
                channelType = key.third.toDomainEnum(),
                routingKeys = rows.map { it[ROUTING_KEY.ROUTING_KEY_] }.toMutableList()
            )
        }.firstOrNull()
    }
}

private fun ChannelType.toDBEnum(): com.ph.syntropyengine.jooq.generated.enums.ChannelType {
    return com.ph.syntropyengine.jooq.generated.enums.ChannelType.valueOf(this.toString())
}

private fun com.ph.syntropyengine.jooq.generated.enums.ChannelType.toDomainEnum(): ChannelType {
    return ChannelType.valueOf(this.toString())
}