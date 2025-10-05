package com.ph.syntropyengine.broker.repository

import com.ph.syntropyengine.broker.model.Channel
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
        val channelRecord = context.insertInto(CHANNELS)
            .set(CHANNELS.CHANNEL_ID, channelId)
            .set(CHANNELS.NAME, channel.name)
            .returning()
            .fetchOne()

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
            Pair(record[CHANNELS.CHANNEL_ID], record[CHANNELS.NAME])
        }.map { (key, rows) ->
            Channel(
                channelId = key.first,
                name = key.second,
                routingKeys = rows.map { it[ROUTING_KEY.ROUTING_KEY_] }.toMutableList()
            )
        }.firstOrNull()
    }
}