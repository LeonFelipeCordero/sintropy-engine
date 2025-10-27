package com.ph.sintropyengine.broker.repository

import com.ph.sintropyengine.broker.model.ConnectionType
import com.ph.sintropyengine.broker.model.Consumer
import com.ph.sintropyengine.jooq.generated.Tables.CHANNELS
import com.ph.sintropyengine.jooq.generated.Tables.CONSUMERS
import com.ph.sintropyengine.jooq.generated.enums.ConsumerConnectionType
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import org.jooq.DSLContext

@ApplicationScoped
class ConsumerRepository(
    private val context: DSLContext,
) {

    fun save(consumer: Consumer): Consumer {
        val consumerId = UUID.randomUUID()
        return context.insertInto(
            CONSUMERS,
            CONSUMERS.CONSUMER_ID,
            CONSUMERS.CHANNEL_ID,
            CONSUMERS.ROUTING_KEY,
            CONSUMERS.CONNECTION_TYPE
        )
            .values(
                consumerId,
                consumer.channelId,
                consumer.routingKey,
                consumer.connectionType.toDBEnum()
            )
            .returning()
            .fetchOneInto(Consumer::class.java)
            ?: throw IllegalStateException("Something went wrong creating a new consumer")
    }

    fun findById(id: UUID): Consumer? =
        context.selectFrom(CONSUMERS)
            .where(CONSUMERS.CONSUMER_ID.eq(id))
            .fetchOneInto(Consumer::class.java)

    fun findByChannel(channelName: String): List<Consumer> =
        context.select(CONSUMERS.asterisk())
            .from(CONSUMERS)
            .leftJoin(CHANNELS).on(CONSUMERS.CHANNEL_ID.eq(CHANNELS.CHANNEL_ID))
            .where(CHANNELS.NAME.eq(channelName))
            .fetchInto(Consumer::class.java)

    fun findAll(): List<Consumer> =
        context.selectFrom(CONSUMERS).fetchInto(Consumer::class.java)

    fun delete(id: UUID) =
        context.deleteFrom(CONSUMERS)
            .where(CONSUMERS.CONSUMER_ID.eq(id))
            .execute()

    fun deleteAll() = context.deleteFrom(CONSUMERS).execute()

    // TODO: leaving here to in case more complex mapping from POJOS to domain objects is needed
//    private fun recordToDto(record: Record): Consumer {
//        return Consumer(
//            consumerId = record["consumer_id"] as UUID,
//            channelId = record["channel_id"] as UUID,
//            routingKey = record["routing_key"] as String,
//        )
//    }
}

private fun ConnectionType.toDBEnum(): ConsumerConnectionType {
    return ConsumerConnectionType.valueOf(this.toString())
}