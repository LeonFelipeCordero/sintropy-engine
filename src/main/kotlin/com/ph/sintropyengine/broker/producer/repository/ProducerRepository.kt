package com.ph.sintropyengine.broker.producer.repository

import com.ph.sintropyengine.broker.producer.model.Producer
import com.ph.sintropyengine.jooq.generated.Tables
import jakarta.enterprise.context.ApplicationScoped
import org.jooq.DSLContext
import java.util.UUID

@ApplicationScoped
class ProducerRepository(
    private val context: DSLContext,
) {
    fun save(producer: Producer): Producer =
        context
            .insertInto(
                Tables.PRODUCERS,
                Tables.PRODUCERS.NAME,
                Tables.PRODUCERS.CHANNEL_ID,
            ).values(producer.name, producer.channelId)
            .returning()
            .fetchOneInto(Producer::class.java)
            ?: throw IllegalStateException("Something went wrong creating a new Producer")

    fun findById(id: UUID): Producer? =
        context
            .selectFrom(Tables.PRODUCERS)
            .where(Tables.PRODUCERS.PRODUCER_ID.eq(id))
            .fetchOneInto(Producer::class.java)

    fun findByName(name: String): Producer? =
        context
            .selectFrom(Tables.PRODUCERS)
            .where(Tables.PRODUCERS.NAME.eq(name))
            .fetchOneInto(Producer::class.java)

    fun findByChannel(channelName: String): List<Producer> =
        context
            .select(Tables.PRODUCERS.asterisk())
            .from(Tables.PRODUCERS)
            .leftJoin(Tables.CHANNELS)
            .on(Tables.PRODUCERS.CHANNEL_ID.eq(Tables.CHANNELS.CHANNEL_ID))
            .where(Tables.CHANNELS.NAME.eq(channelName))
            .fetchInto(Producer::class.java)

    fun findAll(): List<Producer> =
        context
            .selectFrom(Tables.PRODUCERS)
            .fetchInto(Producer::class.java)

    fun delete(id: UUID) =
        context
            .deleteFrom(Tables.PRODUCERS)
            .where(Tables.PRODUCERS.PRODUCER_ID.eq(id))
            .execute()

    fun deleteByName(name: String) =
        context
            .deleteFrom(Tables.PRODUCERS)
            .where(Tables.PRODUCERS.NAME.eq(name))
            .execute()

    fun deleteAll() = context.deleteFrom(Tables.PRODUCERS).execute()
}
