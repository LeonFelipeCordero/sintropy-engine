package com.ph.syntropyengine.broker.replication

import com.ph.syntropyengine.Fixtures
import com.ph.syntropyengine.IntegrationTestBase
import com.ph.syntropyengine.broker.service.ProducerService
import com.ph.syntropyengine.configuration.DatabaseProperties
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired

/**
 * Simple test to verify connection to logical replication
 * A message is published, a message gets streamed. This isolates all parts
 * and only focus testing the replication.
 *
 * Message routing is created in a different class avoiding DB streaming
 * to speed up test suite
 *
// * Create a channel with two keys
// * Create a producer that will publish on both keys
// * Create 3 consumers, 2 to one key 1 to another one
// * Every message broadcasted through the database is handler by the consumer
 */
// TODO: Connection with coroutines is flaky and causing issues in this test
class ReplicationTest : IntegrationTestBase() {

    private lateinit var pgReplicationConsumer: PGReplicationConsumer

    @Autowired
    private lateinit var producerService: ProducerService

    @Autowired
    private lateinit var databaseProperties: DatabaseProperties

    @BeforeEach
    fun setUp() {
        clean()

        pgReplicationConsumer = PGReplicationConsumer.connect(databaseProperties)
    }

    @Test
    fun `A series of messages are published and captured on the other side`() = runTest(timeout = 15.seconds) {
        val (channel, producer) = createChannelWithProducer()

        backgroundScope.launch { pgReplicationConsumer.startConsuming() }

        delay(1.seconds)

        val message1 = producerService.publishMessage(
            Fixtures.createMessage(
                channel.channelId!!,
                producer.producerId!!,
                channel.routingKeys.first()
            )
        )
        val message2 = producerService.publishMessage(
            Fixtures.createMessage(
                channel.channelId,
                producer.producerId,
                channel.routingKeys.first()
            )
        )
        val message3 = producerService.publishMessage(
            Fixtures.createMessage(
                channel.channelId,
                producer.producerId,
                channel.routingKeys.first()
            )
        )

        val streamMessage1 = pgReplicationConsumer.channel.receive()
        val streamMessage2 = pgReplicationConsumer.channel.receive()
        val streamMessage3 = pgReplicationConsumer.channel.receive()

        assertThat(message1).isEqualTo(streamMessage1)
        assertThat(message2).isEqualTo(streamMessage2)
        assertThat(message3).isEqualTo(streamMessage3)
    }


// these are for the routing test
//    @Test
//    fun `should send a message a to a consumer when expecting a message`() {
//
//    }
//
//    @Test
//    fun `should not send a message to a consumer when not expecting a message`() {
//
//    }
//
//    @Test
//    fun `should send a message to all consumers when expecting a message`() {
//
//    }
}