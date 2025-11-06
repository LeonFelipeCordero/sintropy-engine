package com.ph.sintropyengine.broker.repository

import com.ph.sintropyengine.broker.model.Producer
import com.ph.sintropyengine.jooq.generated.Tables.CHANNELS
import com.ph.sintropyengine.jooq.generated.Tables.PRODUCERS
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import org.jooq.DSLContext

@ApplicationScoped
class ProducerRepository(
    private val context: DSLContext,
) {

    fun save(producer: Producer): Producer {
        return context.insertInto(
            PRODUCERS,
            PRODUCERS.NAME,
            PRODUCERS.CHANNEL_ID
        ).values(producer.name, producer.channelId)
            .returning()
            .fetchOneInto(Producer::class.java)
            ?: throw IllegalStateException("Something went wrong creating a new Producer")
    }

    fun findById(id: UUID): Producer? =
        context.selectFrom(PRODUCERS)
            .where(PRODUCERS.PRODUCER_ID.eq(id))
            .fetchOneInto(Producer::class.java)

    fun findByName(name: String): Producer? =
        context.selectFrom(PRODUCERS)
            .where(PRODUCERS.NAME.eq(name))
            .fetchOneInto(Producer::class.java)

    fun findByChannel(channelName: String): List<Producer> =
        context.select(PRODUCERS.asterisk())
            .from(PRODUCERS)
            .leftJoin(CHANNELS).on(PRODUCERS.CHANNEL_ID.eq(CHANNELS.CHANNEL_ID))
            .where(CHANNELS.NAME.eq(channelName))
            .fetchInto(Producer::class.java)

    fun findAll(): List<Producer> =
        context.selectFrom(PRODUCERS).fetchInto(Producer::class.java)

    fun delete(id: UUID) =
        context.deleteFrom(PRODUCERS)
            .where(PRODUCERS.PRODUCER_ID.eq(id))
            .execute()

    fun deleteAll() = context.deleteFrom(PRODUCERS).execute()
}