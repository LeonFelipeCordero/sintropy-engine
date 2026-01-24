package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.Fixtures
import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.consumption.model.MessageStatus
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class DeadLetterQueueServiceTest : IntegrationTestBase() {
    @Inject
    private lateinit var dlqService: DeadLetterQueueService

    @Inject
    private lateinit var pollingQueue: PollingStandardQueue

    @BeforeEach
    fun setUp() {
        clean()
    }

    @Nested
    inner class AutomaticDlqRouting {
        @Test
        fun `should move message to DLQ when marked as failed`() {
            val (channel, producer) = createChannelWithProducer()
            val message = publishMessage(channel, producer)

            pollingQueue.poll(channel.channelId!!, channel.routingKeys.first())

            pollingQueue.markAsFailed(message.messageId)

            val dlqEntry = dlqRepository.findByMessageId(message.messageId)
            assertThat(dlqEntry).isNotNull
            assertThat(dlqEntry!!.messageId).isEqualTo(message.messageId)
            assertThat(dlqEntry.channelId).isEqualTo(channel.channelId)
            assertThat(dlqEntry.routingKey).isEqualTo(channel.routingKeys.first())
            assertThat(dlqEntry.deliveredTimes).isEqualTo(1)

            val messagesInQueue = messageRepository.findAll()
            assertThat(messagesInQueue).isEmpty()
        }

        @Test
        fun `should not keep message_log processed as false for failed messages`() {
            val (channel, producer) = createChannelWithProducer()
            val message = publishMessage(channel, producer)

            pollingQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingQueue.markAsFailed(message.messageId)

            val messageLog = messageRepository.findMessageLogById(message.messageId)
            assertThat(messageLog).isNull()
        }

        @Test
        fun `should mark message_log processed as true for successfully dequeued messages`() {
            val (channel, producer) = createChannelWithProducer()
            val message = publishMessage(channel, producer)

            pollingQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingQueue.dequeue(message.messageId)

            val messageLog = messageRepository.findMessageLogById(message.messageId)
            assertThat(messageLog).isNotNull
            assertThat(messageLog?.processed).isTrue

            val dlqEntry = dlqRepository.findByMessageId(message.messageId)
            assertThat(dlqEntry).isNull()
        }

        @Test
        fun `should preserve message content when moving to DLQ`() {
            val (channel, producer) = createChannelWithProducer()
            val message = publishMessage(channel, producer)

            pollingQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingQueue.markAsFailed(message.messageId)

            val dlqEntry = dlqRepository.findByMessageId(message.messageId)
            assertThat(dlqEntry?.message).isEqualTo(message.message)
            assertThat(dlqEntry?.headers).isEqualTo(message.headers)
        }
    }

    @Nested
    inner class RecoverSingleMessage {
        @Test
        fun `should recover message from DLQ back to messages table`() {
            val (channel, producer) = createChannelWithProducer()
            val message = publishMessage(channel, producer)

            pollingQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingQueue.markAsFailed(message.messageId)

            val recoveredMessage = dlqService.recoverMessage(message.messageId)

            assertThat(recoveredMessage.messageId).isEqualTo(message.messageId)
            assertThat(recoveredMessage.status).isEqualTo(MessageStatus.READY)
            assertThat(recoveredMessage.deliveredTimes).isEqualTo(0)
            assertThat(recoveredMessage.lastDelivered).isNull()

            val messageInQueue = messageRepository.findById(message.messageId)
            assertThat(messageInQueue).isNotNull

            val dlqEntry = dlqRepository.findByMessageId(message.messageId)
            assertThat(dlqEntry).isNull()
        }

        @Test
        fun `should fail when message not in DLQ`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { dlqService.recoverMessage(UUID.randomUUID()) }
                .withMessageContaining("not found in dead letter queue")
        }

        @Test
        fun `recovered message should be pollable again`() {
            val (channel, producer) = createChannelWithProducer()
            val message = publishMessage(channel, producer)

            pollingQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingQueue.markAsFailed(message.messageId)

            dlqService.recoverMessage(message.messageId)

            val polledMessages = pollingQueue.poll(channel.channelId, channel.routingKeys.first())
            assertThat(polledMessages).hasSize(1)
            assertThat(polledMessages.first().messageId).isEqualTo(message.messageId)
        }
    }

    @Nested
    inner class RecoverMultipleMessages {
        @Test
        fun `should recover multiple messages from DLQ`() {
            val (channel, producer) = createChannelWithProducer()
            val message1 = publishMessage(channel, producer)
            val message2 = publishMessage(channel, producer)

            pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 2)
            pollingQueue.markAsFailed(message1.messageId)
            pollingQueue.markAsFailed(message2.messageId)

            val recovered = dlqService.recoverMessages(listOf(message1.messageId, message2.messageId))

            assertThat(recovered).hasSize(2)
            assertThat(recovered.map { it.messageId }).containsExactlyInAnyOrder(
                message1.messageId,
                message2.messageId,
            )

            val dlqEntries = dlqRepository.findAll()
            assertThat(dlqEntries).isEmpty()
        }

        @Test
        fun `should fail when no messages found in DLQ`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { dlqService.recoverMessages(listOf(UUID.randomUUID())) }
                .withMessageContaining("No messages found in dead letter queue")
        }
    }

    @Nested
    inner class RecoverAllForChannel {
        @Test
        fun `should recover all messages for channel and routing key`() {
            val (channel, producer) = createChannelWithProducer()
            val message1 = publishMessage(channel, producer)
            val message2 = publishMessage(channel, producer)
            val message3 = publishMessage(channel, producer)

            pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 3)
            pollingQueue.markAsFailed(message1.messageId)
            pollingQueue.markAsFailed(message2.messageId)
            pollingQueue.markAsFailed(message3.messageId)

            val recovered =
                dlqService.recoverAllForChannelAndRoutingKey(
                    channelName = channel.name,
                    routingKey = channel.routingKeys.first(),
                )

            assertThat(recovered).hasSize(3)

            val polled = pollingQueue.poll(channel.channelId, channel.routingKeys.first(), 10)
            assertThat(polled).hasSize(3)
        }

        @Test
        fun `should not recover messages from other routing keys`() {
            val channel =
                channelRepository.save(
                    Fixtures.createChannel(routingKeys = mutableListOf("key1", "key2")),
                )
            val producer = createProducer()

            val message1 = publishMessage(channel, producer, "key1")
            val message2 = publishMessage(channel, producer, "key2")

            pollingQueue.poll(channel.channelId!!, "key1")
            pollingQueue.poll(channel.channelId, "key2")
            pollingQueue.markAsFailed(message1.messageId)
            pollingQueue.markAsFailed(message2.messageId)

            dlqService.recoverAllForChannelAndRoutingKey(channel.name, "key1")

            val polledKey1 = pollingQueue.poll(channel.channelId, "key1")
            assertThat(polledKey1).hasSize(1)

            val dlqKey2 = dlqRepository.findByChannelIdAndRoutingKey(channel.channelId, "key2")
            assertThat(dlqKey2).hasSize(1)
        }

        @Test
        fun `should return empty list when no messages in DLQ`() {
            val channel = createStandardQueueChannel()

            val recovered =
                dlqService.recoverAllForChannelAndRoutingKey(
                    channelName = channel.name,
                    routingKey = channel.routingKeys.first(),
                )

            assertThat(recovered).isEmpty()
        }

        @Test
        fun `should fail when channel not found`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    dlqService.recoverAllForChannelAndRoutingKey("non-existent", "key")
                }.withMessageContaining("Channel with name non-existent and routing key key not found")
        }

        @Test
        fun `should fail when routing key not in channel`() {
            val channel = createStandardQueueChannel()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    dlqService.recoverAllForChannelAndRoutingKey(channel.name, "invalid-key")
                }.withMessageContaining("Channel with name ${channel.name} and routing key invalid-key not found")
        }

        @Test
        fun `should recover messages ordered by original timestamp`() {
            val (channel, producer) = createChannelWithProducer()

            val message1 = publishMessage(channel, producer)
            Thread.sleep(10)
            val message2 = publishMessage(channel, producer)
            Thread.sleep(10)
            val message3 = publishMessage(channel, producer)

            pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 3)
            pollingQueue.markAsFailed(message3.messageId)
            pollingQueue.markAsFailed(message1.messageId)
            pollingQueue.markAsFailed(message2.messageId)

            val recovered =
                dlqService.recoverAllForChannelAndRoutingKey(
                    channelName = channel.name,
                    routingKey = channel.routingKeys.first(),
                )

            assertThat(recovered).hasSize(3)
            assertThat(recovered.map { it.messageId }).containsExactly(
                message1.messageId,
                message2.messageId,
                message3.messageId,
            )

            val polled = pollingQueue.poll(channel.channelId, channel.routingKeys.first(), 10)
            assertThat(polled).hasSize(3)
            assertThat(polled.map { it.messageId }).containsExactly(
                message1.messageId,
                message2.messageId,
                message3.messageId,
            )
        }
    }

    @Nested
    inner class FindMessages {
        @Test
        fun `should list DLQ messages for channel and routing key`() {
            val (channel, producer) = createChannelWithProducer()
            val message1 = publishMessage(channel, producer)
            val message2 = publishMessage(channel, producer)

            pollingQueue.poll(channel.channelId!!, channel.routingKeys.first(), 2)
            pollingQueue.markAsFailed(message1.messageId)
            pollingQueue.markAsFailed(message2.messageId)

            val dlqMessages =
                dlqService.findByChannelAndRoutingKey(
                    channelName = channel.name,
                    routingKey = channel.routingKeys.first(),
                )

            assertThat(dlqMessages).hasSize(2)
        }

        @Test
        fun `should paginate DLQ messages`() {
            val (channel, producer) = createChannelWithProducer()
            repeat(5) {
                val msg = publishMessage(channel, producer)
                pollingQueue.poll(channel.channelId!!, channel.routingKeys.first())
                pollingQueue.markAsFailed(msg.messageId)
            }

            val page0 =
                dlqService.findByChannelAndRoutingKey(
                    channelName = channel.name,
                    routingKey = channel.routingKeys.first(),
                    pageSize = 2,
                    page = 0,
                )
            val page1 =
                dlqService.findByChannelAndRoutingKey(
                    channelName = channel.name,
                    routingKey = channel.routingKeys.first(),
                    pageSize = 2,
                    page = 1,
                )

            assertThat(page0).hasSize(2)
            assertThat(page1).hasSize(2)
            assertThat(page0.map { it.messageId }).doesNotContainAnyElementsOf(page1.map { it.messageId })
        }
    }
}
