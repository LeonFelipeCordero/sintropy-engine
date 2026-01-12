package com.ph.sintropyengine.broker.channel.service

import com.ph.sintropyengine.IntegrationTestBase
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class ChannelLinkServiceTest : IntegrationTestBase() {
    @Inject
    private lateinit var channelLinkService: ChannelLinkService

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

            assertThat(link.channelLinkId).isNotNull()
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

            assertThat(link.channelLinkId).isNotNull()
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

            assertThat(link.channelLinkId).isNotNull()
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

            assertThat(link.channelLinkId).isNotNull()
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

            assertThat(link.channelLinkId).isNotNull()
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

            assertThat(link.channelLinkId).isNotNull()
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
                }.withMessage("Source channel with name non-existent-channel not found")
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
                }.withMessage("Target channel with name non-existent-channel not found")
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
                }.withMessageContaining("Source routing key invalid.routing.key does not exist")
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
                }.withMessageContaining("Target routing key invalid.routing.key does not exist")
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
            val found = channelLinkService.findById(UUID.randomUUID())

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

            channelLinkService.unlinkChannels(link.channelLinkId!!)

            val found = channelLinkService.findById(link.channelLinkId!!)
            assertThat(found).isNull()
        }

        @Test
        fun `should fail unlink when link does not exist`() {
            val nonExistentId = UUID.randomUUID()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy {
                    channelLinkService.unlinkChannels(nonExistentId)
                }.withMessage("Channel link with id $nonExistentId not found")
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

            channelLinkService.enableLink(link.channelLinkId!!)

            val found = channelLinkService.findById(link.channelLinkId!!)
            assertThat(found?.enabled).isTrue()
        }

        @Test
        fun `should disable link`() {
            val source = createStandardQueueChannel()
            val target = createStandardQueueChannel()
            val link = createChannelLink(source, target)

            channelLinkService.disableLink(link.channelLinkId!!)

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
}
