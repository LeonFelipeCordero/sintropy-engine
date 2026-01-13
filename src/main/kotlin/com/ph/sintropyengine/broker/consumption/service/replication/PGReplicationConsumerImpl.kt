package com.ph.sintropyengine.broker.consumption.service.replication

import com.fasterxml.jackson.databind.ObjectMapper
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.model.MessageStatus
import com.ph.sintropyengine.broker.shared.configuration.CustomObjectMapper
import com.ph.sintropyengine.broker.shared.configuration.DatabaseProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jooq.JSONB
import org.postgresql.PGConnection
import org.postgresql.PGProperty
import org.postgresql.replication.PGReplicationStream
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

class PGReplicationConsumerImpl private constructor(
    val channel: Channel<Message>,
    private val objectMapper: ObjectMapper,
    private val databaseProperties: DatabaseProperties,
) : PGReplicationConsumer {
    private lateinit var connectionAndReplication: ConnectionAndReplication

    constructor(databaseProperties: DatabaseProperties) : this(
        Channel<Message>(),
        CustomObjectMapper(),
        databaseProperties,
    ) {
        this.connectionAndReplication = preparePGStream()
        logger.info { "Connection established for streaming..." }
    }

    override suspend fun startConsuming() =
        withContext(Dispatchers.IO) {
            connectionAndReplication.connection.use {
                while (true) {
                    val message = connectionAndReplication.replicationStream.readPending()
                    if (message == null) {
                        delay(10)
                        continue
                    }
                    val json =
                        StandardCharsets.UTF_8
                            .decode(message)
                            .toString()
                            .trim()

                    if (json.isEmpty()) {
                        logger.debug { "Receiving empty json from decoding" }
                        continue
                    }

                    val root = objectMapper.readTree(json)
                    val changes = root["change"]

                    if (changes == null || !changes.isArray || changes.isEmpty) {
                        logger.debug { "Received change list ${changes.toPrettyString()}" }
                        continue
                    }

                    for (change in changes) {
                        val kind = change["kind"]?.asText()
                        if (kind != "insert") {
                            logger.debug { "Received a non insert event $kind" }
                            continue
                        }

                        val columnNames = change["columnnames"]?.map { it.asText() } ?: continue
                        val columnValues = change["columnvalues"]?.map { it.asText() } ?: continue

                        val record = columnNames.zip(columnValues).toMap()

                        channel.send(record.toMessage())
                        logger.debug { "New record: $record" }
                    }

                    connectionAndReplication.replicationStream.setAppliedLSN(connectionAndReplication.replicationStream.lastReceiveLSN)
                    connectionAndReplication.replicationStream.setFlushedLSN(connectionAndReplication.replicationStream.lastReceiveLSN)
                }
            }
        }

    override fun channel(): Channel<Message> = channel

    private fun preparePGStream(): ConnectionAndReplication {
        val properties = Properties()
        PGProperty.USER.set(properties, databaseProperties.username())
        PGProperty.PASSWORD.set(properties, databaseProperties.password())
        PGProperty.REPLICATION.set(properties, "database")
        PGProperty.ASSUME_MIN_SERVER_VERSION.set(properties, "9.4")
        PGProperty.PREFER_QUERY_MODE.set(properties, "simple")

        val connection = DriverManager.getConnection(databaseProperties.jdbcUrl(), properties)
        val pgConnection = connection.unwrap(PGConnection::class.java)

        try {
            pgConnection.replicationAPI.dropReplicationSlot("messages_slot")
        } catch (e: SQLException) {
            if (e.message?.contains("does not exist") != true) {
                throw RuntimeException("Failed to drop REPLICATION slot deletion", e)
            }
        }

        pgConnection.replicationAPI
            .createReplicationSlot()
            .logical()
            .withSlotName("messages_slot")
            .withOutputPlugin("wal2json")
            .make()

        val replicationStream =
            pgConnection.replicationAPI
                .replicationStream()
                .logical()
                .withSlotName("messages_slot")
                .withSlotOption("actions", "insert")
                .withSlotOption("add-tables", "public.messages")
                .withStatusInterval(20, TimeUnit.SECONDS)
                .start()

        return ConnectionAndReplication(connection, replicationStream)
    }
}

private fun Map<String, String>.toMessage(): Message =
    Message(
        messageId = UUID.fromString(this["message_id"]),
        timestamp =
            this["timestamp"]?.let { OffsetDateTime.parse(it.replace(' ', 'T')) }
                ?: throw IllegalStateException("Timestamp missing in message streaming"),
        channelId = UUID.fromString(this["channel_id"]),
        producerId = UUID.fromString(this["producer_id"]),
        routingKey = this["routing_key"] ?: throw IllegalStateException("routing_key missing in message streaming"),
        message = JSONB.jsonb(this["message"] ?: throw IllegalStateException("message missing in message streaming")),
        headers = JSONB.jsonb(this["headers"] ?: throw IllegalStateException("headers missing in message streaming")),
        status =
            this["status"]?.let { MessageStatus.valueOf(it) }
                ?: throw IllegalStateException("status missing in message streaming"),
        lastDelivered =
            this["last_delivered"]?.let {
                if (it == "null") {
                    null
                } else {
                    OffsetDateTime.parse(it.replace(' ', 'T'))
                }
            },
        deliveredTimes =
            this["delivered_times"]?.toInt()
                ?: throw IllegalStateException("delivered times missing in message streaming"),
    )

data class ConnectionAndReplication(
    val connection: Connection,
    val replicationStream: PGReplicationStream,
)
