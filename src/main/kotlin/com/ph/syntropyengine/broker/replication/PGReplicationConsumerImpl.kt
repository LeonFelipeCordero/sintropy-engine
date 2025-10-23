package com.ph.syntropyengine.broker.replication

import com.fasterxml.jackson.databind.ObjectMapper
import com.ph.syntropyengine.configuration.CustomObjectMapper
import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.configuration.DatabaseProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.postgresql.PGConnection
import org.postgresql.PGProperty
import org.postgresql.replication.PGReplicationStream
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

private val logger = KotlinLogging.logger {}

class PGReplicationConsumerImpl private constructor(
    val channel: Channel<Message>,
    private val objectMapper: ObjectMapper,
    private val databaseProperties: DatabaseProperties
) : PGReplicationConsumer {
    private lateinit var connectionAndReplication: ConnectionAndReplication

    constructor(databaseProperties: DatabaseProperties) : this(
        Channel<Message>(),
        CustomObjectMapper(),
        databaseProperties
    ) {
        this.connectionAndReplication = preparePGStream()
        logger.info { "Connection established for streaming..." }
    }

    override suspend fun startConsuming() = withContext(Dispatchers.IO) {
        connectionAndReplication.connection.use {
            while (true) {
                val message = connectionAndReplication.replicationStream.readPending()
                if (message == null) {
                    delay(10)
                    continue
                }
                val json = StandardCharsets.UTF_8.decode(message).toString().trim()

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

                    // todo this ?: continue sucks
                    val columnNames = change["columnnames"]?.map { it.asText() } ?: continue
                    val columnValues = change["columnvalues"]?.map { it.asText() } ?: continue

                    val record = columnNames.zip(columnValues).toMap()

                    logger.debug { "New record: $record" }
                    channel.send(record.toMessage())
                }

                connectionAndReplication.replicationStream.setAppliedLSN(connectionAndReplication.replicationStream.lastReceiveLSN)
                connectionAndReplication.replicationStream.setFlushedLSN(connectionAndReplication.replicationStream.lastReceiveLSN)
            }

        }
    }

    override fun channel(): Channel<Message> {
        return channel
    }

    private fun preparePGStream(): ConnectionAndReplication {
        val properties = Properties()
        PGProperty.USER.set(properties, databaseProperties.username)
        PGProperty.PASSWORD.set(properties, databaseProperties.password)
        PGProperty.REPLICATION.set(properties, "database")
        PGProperty.ASSUME_MIN_SERVER_VERSION.set(properties, "9.4");
        PGProperty.PREFER_QUERY_MODE.set(properties, "simple")

        val connection = DriverManager.getConnection(databaseProperties.url, properties)
        val pgConnection = connection.unwrap(PGConnection::class.java)

        try {
            pgConnection.replicationAPI.dropReplicationSlot("messages_slot")
        } catch (e: SQLException) {
            logger.debug { e.message }
        }

        pgConnection.replicationAPI
            .createReplicationSlot()
            .logical()
            .withSlotName("messages_slot")
            .withOutputPlugin("wal2json")
            .make()

        val replicationStream = pgConnection.replicationAPI
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

private fun Map<String, String>.toMessage(): Message {
    return Message(
        messageId = UUID.fromString(this["message_id"]),
        timestamp = OffsetDateTime.parse(
            this["timestamp"]?.replace(' ', 'T')
                ?: throw IllegalStateException("timestamp missing in message streaming"),
        ),
        channelId = UUID.fromString(this["channel_id"]),
        producerId = UUID.fromString(this["producer_id"]),
        routingKey = this["routing_key"] ?: throw IllegalStateException("routing_key missing in message streaming"),
        message = this["message"] ?: throw IllegalStateException("message missing in message streaming"),
        headers = this["headers"] ?: throw IllegalStateException("headers missing in message streaming"),
    )
}

data class ConnectionAndReplication(
    val connection: Connection,
    val replicationStream: PGReplicationStream
)