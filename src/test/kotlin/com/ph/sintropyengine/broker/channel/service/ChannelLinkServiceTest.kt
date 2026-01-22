package com.ph.sintropyengine.broker.channel.service

import com.ph.sintropyengine.IntegrationTestBase
import io.quarkus.test.junit.QuarkusTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

@QuarkusTest
class ChannelLinkServiceTest : IntegrationTestBase() {
    @BeforeEach
    fun setUp() {
        clean()
    }

    @Nested
    inner class LinkCompatibility {
        @Test
        fun `should link Standard Queue to Standard Queue`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()

            val link =
                channelLinkService.linkChannels(
                    source.name,
                    target.name,
                    source.routingKeys.first(),
                    target.routingKeys.first(),
                )

            assertThat(link.channelLinkId).isNotNull()
            assertThat(link.sourceChannelId).isEqualTo(source.channelId)
            assertThat(link.targetChannelId).isEqualTo(target.channelId)
            assertThat(link.enabled).isTrue()
        }

        @Test
        fun `should link FIFO Queue to FIFO Queue`() {
            val source = createFifoQueueChannel()
            val target = createFifoQueueChannel()

            val link =
                channelLinkService.linkChannels(
                    source.name,
                    target.name,
                    source.routingKeys.first(),
                    target.routingKeys.first(),
                )

            assertThat(link.channelLinkUuid).isNotNull()
        }

        @Test
        fun `should link FIFO Queue to Standard Queue`() {
            val source = createFifoQueueChannel()
            val target = createStandardQueueChannel()

            val link =
                channelLinkService.linkChannels(
                    source.name,
                    target.name,
                    source.routingKeys.first(),
                    target.routingKeys.first(),
                )

            assertThat(link.channelLinkUuid).isNotNull()
        }

        @Test
        fun `should link FIFO Queue to Stream`() {
            val source = createFifoQueueChannel()
            val target = createStreamChannel()

            val link =
                channelLinkService.linkChannels(
                    source.name,
                    target.name,
                    source.routingKeys.first(),
                    target.routingKeys.first(),
                )

            assertThat(link.channelLinkUuid).isNotNull()
        }

        @Test
        fun `should link Stream to Stream`() {
            val source = createStreamChannel()
            val target = createStreamChannel()

            val link =
                channelLinkService.linkChannels(
                    source.name,
                    target.name,
                    source.routingKeys.first(),
                    target.routingKeys.first(),
                )

            assertThat(link.channelLinkUuid).isNotNull()
        }

        @Test
        fun `should link Stream to FIFO Queue`() {
            val source = createStreamChannel()
            val target = createFifoQueueChannel()

            val link =
                channelLinkService.linkChannels(
                    source.name,
                    target.name,
                    source.routingKeys.first(),
                    target.routingKeys.first(),
                )

            assertThat(link.channelLinkUuid).isNotNull()
        }

        @Test
        fun `should link Stream to Standard Queue`() {
            val source = createStreamChannel()
            val target = createStandardQueueChannel()

            val link =
                channelLinkService.linkChannels(
                    source.name,
                    target.name,
                    source.routingKeys.first(),
                    target.routingKeys.first(),
                )

            assertThat(link.channelLinkUuid).isNotNull()
        }

        @Test
        fun `should fail when linking Standard Queue to FIFO Queue`() {
            val source = createStandardQueueChannel()
            val target = createFifoQueueChannel()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.linkChannels(
                        source.name,
                        target.name,
                        source.routingKeys.first(),
                        target.routingKeys.first(),
                    )
                }.withMessageContaining("Cannot link Standard Queue to FIFO Queue")
                .withMessageContaining("Standard Queues don't guarantee message ordering")
        }

        @Test
        fun `should fail when linking Standard Queue to Stream`() {
            val source = createStandardQueueChannel()
            val target = createStreamChannel()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.linkChannels(
                        source.name,
                        target.name,
                        source.routingKeys.first(),
                        target.routingKeys.first(),
                    )
                }.withMessageContaining("Cannot link Standard Queue to Stream")
                .withMessageContaining("Standard Queues don't guarantee message ordering")
        }
    }

    @Nested
    inner class Validation {
        @Test
        fun `should fail when source channel does not exist`() {
            val target = createStandardQueueChannel()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.linkChannels(
                        "non-existent-channel",
                        target.name,
                        "some.key",
                        target.routingKeys.first(),
                    )
                }.withMessageContaining("Channel with name non-existent-channel and routing key some.key not found")
        }

        @Test
        fun `should fail when target channel does not exist`() {
            val source = createStandardQueueChannel()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.linkChannels(
                        source.name,
                        "non-existent-channel",
                        source.routingKeys.first(),
                        "some.key",
                    )
                }.withMessageContaining("Channel with name non-existent-channel and routing key some.key not found")
        }

        @Test
        fun `should fail when source routing key does not exist`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.linkChannels(
                        source.name,
                        target.name,
                        "invalid.routing.key",
                        target.routingKeys.first(),
                    )
                }.withMessageContaining("Channel with name ${source.name} and routing key invalid.routing.key not found")
        }

        @Test
        fun `should fail when target routing key does not exist`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.linkChannels(
                        source.name,
                        target.name,
                        source.routingKeys.first(),
                        "invalid.routing.key",
                    )
                }.withMessageContaining("Channel with name ${target.name} and routing key invalid.routing.key not found")
        }
    }

    @Nested
    inner class FindOperations {
        @Test
        fun `should find link by id`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val link = createChannelLink(source, target)

            val found = channelLinkService.findById(link.channelLinkId!!)

            assertThat(found).isNotNull
            assertThat(found?.channelLinkId).isEqualTo(link.channelLinkId)
            assertThat(found?.sourceChannelId).isEqualTo(source.channelId)
            assertThat(found?.targetChannelId).isEqualTo(target.channelId)
        }

        @Test
        fun `should return null when link not found`() {
            val found = channelLinkService.findById(Random.nextLong())

            assertThat(found).isNull()
        }

        @Test
        fun `should get outgoing links from channel`() {
            val source = createStandardQueueChannel()
            val target1 = createStandardQueueChannel()
            val target2 = createStandardQueueChannel()

            createChannelLink(source, target1)
            createChannelLink(source, target2)

            val links = channelLinkService.getLinksFromChannel(source.name)

            assertThat(links).hasSize(2)
            assertThat(links.map { it.sourceChannelId }).containsOnly(source.channelId)
        }

        @Test
        fun `should return empty list when no outgoing links`() {
            val channel = createStandardQueueChannel()

            val links = channelLinkService.getLinksFromChannel(channel.name)

            assertThat(links).isEmpty()
        }

        @Test
        fun `should get incoming links to channel`() {
            val source1 = createStandardQueueChannel()
            val source2 = createStandardQueueChannel()
            val target = createStandardQueueChannel()

            createChannelLink(source1, target)
            createChannelLink(source2, target)

            val links = channelLinkService.getLinksToChannel(target.name)

            assertThat(links).hasSize(2)
            assertThat(links.map { it.targetChannelId }).containsOnly(target.channelId)
        }

        @Test
        fun `should return empty list when no incoming links`() {
            val channel = createStandardQueueChannel()

            val links = channelLinkService.getLinksToChannel(channel.name)

            assertThat(links).isEmpty()
        }

        @Test
        fun `should fail get outgoing links when channel does not exist`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.getLinksFromChannel("non-existent-channel")
                }.withMessage("Channel with name non-existent-channel not found")
        }

        @Test
        fun `should fail get incoming links when channel does not exist`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.getLinksToChannel("non-existent-channel")
                }.withMessage("Channel with name non-existent-channel not found")
        }
    }

    @Nested
    inner class UnlinkOperations {
        @Test
        fun `should unlink channels`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val link = createChannelLink(source, target)

            channelLinkService.unlinkChannels(link.channelLinkUuid!!)

            val found = channelLinkService.findById(link.channelLinkId!!)
            assertThat(found).isNull()
        }

        @Test
        fun `should fail unlink when link does not exist`() {
            val nonExistentId = UUID.randomUUID()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.unlinkChannels(nonExistentId)
                }.withMessage("Channel link with uuid $nonExistentId not found")
        }
    }

    @Nested
    inner class EnableDisableOperations {
        @Test
        fun `should enable link`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val link = createChannelLink(source, target)
            channelLinkRepository.setEnabled(link.channelLinkId!!, false)

            channelLinkService.enableLink(link.channelLinkUuid!!)

            val found = channelLinkService.findById(link.channelLinkId!!)
            assertThat(found?.enabled).isTrue()
        }

        @Test
        fun `should disable link`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val link = createChannelLink(source, target)

            channelLinkService.disableLink(link.channelLinkUuid!!)

            val found = channelLinkService.findById(link.channelLinkId!!)
            assertThat(found?.enabled).isFalse()
        }

        @Test
        fun `should fail enable when link does not exist`() {
            val nonExistentId = UUID.randomUUID()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.enableLink(nonExistentId)
                }.withMessage("Channel link with id $nonExistentId not found")
        }

        @Test
        fun `should fail disable when link does not exist`() {
            val nonExistentId = UUID.randomUUID()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.disableLink(nonExistentId)
                }.withMessage("Channel link with id $nonExistentId not found")
        }
    }

    @Nested
    inner class MessageRouting {
        @Test
        fun `should route message to linked channel`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val producer = createProducer(source)

            createChannelLink(source, target)

            val originalMessage = publishMessage(source, producer)

            val allMessages = messageRepository.findAll()
            assertThat(allMessages).hasSize(2)

            val routedMessage = allMessages.find { it.channelId == target.channelId }
            assertThat(routedMessage).isNotNull
            assertThat(routedMessage?.routingKey).isEqualTo(target.routingKeys.first())
            assertThat(routedMessage?.message).isEqualTo(originalMessage.message)
            assertThat(routedMessage?.originMessageId).isEqualTo(originalMessage.messageUuid)
        }

        @Test
        fun `should route message to multiple linked channels`() {
            val source = createStandardQueueChannel()
            val target1 = createStandardQueueChannel()
            val target2 = createStandardQueueChannel()
            val producer = createProducer(source)

            createChannelLink(source, target1)
            createChannelLink(source, target2)

            publishMessage(source, producer)

            val allMessages = messageRepository.findAll()
            assertThat(allMessages).hasSize(3)

            val routedToTarget1 = allMessages.find { it.channelId == target1.channelId }
            val routedToTarget2 = allMessages.find { it.channelId == target2.channelId }

            assertThat(routedToTarget1).isNotNull
            assertThat(routedToTarget2).isNotNull
        }

        @Test
        fun `should not route when link is disabled`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val producer = createProducer(source)

            val link = createChannelLink(source, target)
            channelLinkRepository.setEnabled(link.channelLinkId!!, false)

            publishMessage(source, producer)

            val allMessages = messageRepository.findAll()
            assertThat(allMessages).hasSize(1)
            assertThat(allMessages.first().channelId).isEqualTo(source.channelId)
        }

        @Test
        fun `should not route already routed messages - prevent infinite loops`() {
            val channel1 = createStandardQueueChannel()
            val channel2 = createStandardQueueChannel()
            val producer = createProducer(channel1)

            // Create bidirectional links
            createChannelLink(channel1, channel2)
            createChannelLink(channel2, channel1)

            publishMessage(channel1, producer)

            // Should only have 2 messages: original + one routed
            // The routed message should NOT trigger another route back
            val allMessages = messageRepository.findAll()
            assertThat(allMessages).hasSize(2)
        }

        @Test
        fun `should route with correct target routing key`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val producer = createProducer(source)

            val targetRoutingKey = target.routingKeys.first()
            createChannelLink(source, target, source.routingKeys.first(), targetRoutingKey)

            publishMessage(source, producer)

            val routedMessage = messageRepository.findAll().find { it.channelId == target.channelId }
            assertThat(routedMessage?.routingKey).isEqualTo(targetRoutingKey)
        }

        @Test
        fun `should preserve message content when routing`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val producer = createProducer(source)

            createChannelLink(source, target)

            val originalMessage = publishMessage(source, producer)

            val routedMessage = messageRepository.findAll().find { it.channelId == target.channelId }
            assertThat(routedMessage?.message).isEqualTo(originalMessage.message)
            assertThat(routedMessage?.headers).isEqualTo(originalMessage.headers)
        }

        @Test
        fun `should also log routed messages to message_log`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val producer = createProducer(source)

            createChannelLink(source, target)

            publishMessage(source, producer)

            val allMessageLogs = messageRepository.findAllMessageLog()
            assertThat(allMessageLogs).hasSize(2)
        }
    }
}
