package com.ph.sintropyengine.broker.replication

//import com.ph.sintropyengine.Fixtures
//import com.ph.sintropyengine.IntegrationTestBase
//import com.ph.sintropyengine.configuration.DatabaseProperties
//import io.quarkus.test.junit.QuarkusTest
//import jakarta.inject.Inject
//import kotlin.time.Duration.Companion.seconds
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.test.runTest
//import org.assertj.core.api.Assertions.assertThat
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test

//@QuarkusTest
//class ReplicationTest : IntegrationTestBase() {
//
//    private lateinit var pgReplicationConsumerImpl: PGReplicationConsumerImpl
//
//    @Inject
//    private lateinit var databaseProperties: DatabaseProperties
//
//    @BeforeEach
//    fun setUp() {
//        clean()
//
//        pgReplicationConsumerImpl = PGReplicationConsumerImpl(databaseProperties)
//    }
//
//    @Test
//    fun `A series of messages are published and captured on the other side`() = runTest(timeout = 15.seconds) {
//        val (channel, producer) = createChannelWithProducer()
//
//        backgroundScope.launch { pgReplicationConsumerImpl.startConsuming() }
//
//        delay(1.seconds)
//
//        val message1 = producerService.publishMessage(
//            Fixtures.createMessage(
//                channel.channelId!!,
//                producer.producerId!!,
//                channel.routingKeys.first()
//            )
//        )
//        val message2 = producerService.publishMessage(
//            Fixtures.createMessage(
//                channel.channelId,
//                producer.producerId,
//                channel.routingKeys.first()
//            )
//        )
//        val message3 = producerService.publishMessage(
//            Fixtures.createMessage(
//                channel.channelId,
//                producer.producerId,
//                channel.routingKeys.first()
//            )
//        )
//
//        val streamMessage1 = pgReplicationConsumerImpl.channel.receive()
//        val streamMessage2 = pgReplicationConsumerImpl.channel.receive()
//        val streamMessage3 = pgReplicationConsumerImpl.channel.receive()
//
//        assertThat(message1).usingRecursiveComparison().isEqualTo(streamMessage1)
//        assertThat(message2).usingRecursiveComparison().isEqualTo(streamMessage2)
//        assertThat(message3).usingRecursiveComparison().isEqualTo(streamMessage3)
//    }
//}