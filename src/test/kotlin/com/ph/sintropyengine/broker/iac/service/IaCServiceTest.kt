package com.ph.sintropyengine.broker.iac.service

import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.iac.model.ChannelIaC
import com.ph.sintropyengine.broker.iac.model.ChannelLinkIaC
import com.ph.sintropyengine.broker.iac.model.IaC
import com.ph.sintropyengine.broker.iac.model.ProducerIaC
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

@QuarkusTest
class IaCServiceTest : IntegrationTestBase() {
    @Inject
    private lateinit var iaCService: IaCService

    @BeforeEach
    fun setUp() {
        clean()
    }

    @Nested
    inner class CreateResources {
        @Test
        fun `should create channels from IaC definition`() {
            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "test-channel",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("key1", "key2"),
                            ),
                        ),
                    producers = emptyList(),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            val channels = channelService.findAll()
            assertThat(channels).hasSize(1)
            assertThat(channels.first().name).isEqualTo("test-channel")
            assertThat(channels.first().routingKeys).containsExactlyInAnyOrder("key1", "key2")
        }

        @Test
        fun `should create producers from IaC definition`() {
            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "test-channel",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("key1"),
                            ),
                        ),
                    producers =
                        listOf(
                            ProducerIaC(
                                name = "test-producer",
                                channelName = "test-channel",
                            ),
                        ),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            val producers = producerService.findAll()
            assertThat(producers).hasSize(1)
            assertThat(producers.first().name).isEqualTo("test-producer")
        }

        @Test
        fun `should create channel links from IaC definition`() {
            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "source-channel",
                                channelType = "QUEUE",
                                consumptionType = "FIFO",
                                routingKeys = listOf("source-key"),
                            ),
                            ChannelIaC(
                                name = "target-channel",
                                channelType = "QUEUE",
                                consumptionType = "FIFO",
                                routingKeys = listOf("target-key"),
                            ),
                        ),
                    producers = emptyList(),
                    channelLinks =
                        listOf(
                            ChannelLinkIaC(
                                sourceChannelName = "source-channel",
                                targetChannelName = "target-channel",
                                sourceRoutingKey = "source-key",
                                targetRoutingKey = "target-key",
                            ),
                        ),
                )

            iaCService.applyChanges(iac)

            val links = channelLinkService.findAll()
            assertThat(links).hasSize(1)
            assertThat(links.first().sourceRoutingKey).isEqualTo("source-key")
            assertThat(links.first().targetRoutingKey).isEqualTo("target-key")
        }

        @Test
        fun `should create full infrastructure from IaC definition`() {
            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "orders",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("new", "processed"),
                            ),
                            ChannelIaC(
                                name = "notifications",
                                channelType = "QUEUE",
                                consumptionType = "FIFO",
                                routingKeys = listOf("email"),
                            ),
                        ),
                    producers =
                        listOf(
                            ProducerIaC(name = "order-service", channelName = "orders"),
                            ProducerIaC(name = "notification-service", channelName = "notifications"),
                        ),
                    channelLinks =
                        listOf(
                            ChannelLinkIaC(
                                sourceChannelName = "notifications",
                                targetChannelName = "orders",
                                sourceRoutingKey = "email",
                                targetRoutingKey = "processed",
                            ),
                        ),
                )

            iaCService.applyChanges(iac)

            assertThat(channelService.findAll()).hasSize(2)
            assertThat(producerService.findAll()).hasSize(2)
            assertThat(channelLinkService.findAll()).hasSize(1)
        }
    }

    @Nested
    inner class DeleteResources {
        @Test
        fun `should delete channels not in IaC definition`() {
            // Create initial state
            channelService.createChannel(
                name = "existing-channel",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD,
            )

            val iac =
                IaC(
                    channels = emptyList(),
                    producers = emptyList(),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            assertThat(channelService.findAll()).isEmpty()
        }

        @Test
        fun `should delete producers not in IaC definition`() {
            // Create initial state
            val channel =
                channelService.createChannel(
                    name = "test-channel",
                    channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                    routingKeys = listOf("key1"),
                    consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD,
                )
            producerService.createProducer("old-producer", "test-channel")

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "test-channel",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("key1"),
                            ),
                        ),
                    producers = emptyList(),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            assertThat(producerService.findAll()).isEmpty()
            assertThat(channelService.findAll()).hasSize(1)
        }

        @Test
        fun `should delete channel links not in IaC definition`() {
            // Create initial state
            val source =
                channelService.createChannel(
                    name = "source",
                    channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                    routingKeys = listOf("key1"),
                    consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
                )
            val target =
                channelService.createChannel(
                    name = "target",
                    channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                    routingKeys = listOf("key1"),
                    consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
                )
            channelLinkService.linkChannels("source", "target", "key1", "key1")

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(name = "source", channelType = "QUEUE", consumptionType = "FIFO", routingKeys = listOf("key1")),
                            ChannelIaC(name = "target", channelType = "QUEUE", consumptionType = "FIFO", routingKeys = listOf("key1")),
                        ),
                    producers = emptyList(),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            assertThat(channelLinkService.findAll()).isEmpty()
            assertThat(channelService.findAll()).hasSize(2)
        }
    }

    @Nested
    inner class ChannelDiffLogic {
        @Test
        fun `should not recreate existing channels`() {
            val existingChannel =
                channelService.createChannel(
                    name = "existing-channel",
                    channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                    routingKeys = listOf("key1"),
                    consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD,
                )
            val originalId = existingChannel.channelId

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "existing-channel",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("key1"),
                            ),
                        ),
                    producers = emptyList(),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            val channels = channelService.findAll()
            assertThat(channels).hasSize(1)
            assertThat(channels.first().channelId).isEqualTo(originalId)
        }

        @Test
        fun `should add new channels and keep existing ones`() {
            channelService.createChannel(
                name = "existing-channel",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD,
            )

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "existing-channel",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("key1"),
                            ),
                            ChannelIaC(
                                name = "new-channel",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("key2"),
                            ),
                        ),
                    producers = emptyList(),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            val channels = channelService.findAll()
            assertThat(channels).hasSize(2)
            assertThat(channels.map { it.name }).containsExactlyInAnyOrder("existing-channel", "new-channel")
        }

        @Test
        fun `should handle channel diff with creates and deletes`() {
            channelService.createChannel(
                name = "keep-channel",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD,
            )
            channelService.createChannel(
                name = "delete-channel",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD,
            )

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "keep-channel",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("key1"),
                            ),
                            ChannelIaC(
                                name = "new-channel",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("key1"),
                            ),
                        ),
                    producers = emptyList(),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            val channels = channelService.findAll()
            assertThat(channels).hasSize(2)
            assertThat(channels.map { it.name }).containsExactlyInAnyOrder("keep-channel", "new-channel")
            assertThat(channels.map { it.name }).doesNotContain("delete-channel")
        }
    }

    @Nested
    inner class ProducerDiffLogic {
        @Test
        fun `should not recreate existing producers`() {
            channelService.createChannel(
                name = "test-channel",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD,
            )
            val existingProducer = producerService.createProducer("existing-producer", "test-channel")
            val originalId = existingProducer.producerId

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "test-channel",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("key1"),
                            ),
                        ),
                    producers =
                        listOf(
                            ProducerIaC(name = "existing-producer", channelName = "test-channel"),
                        ),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            val producers = producerService.findAll()
            assertThat(producers).hasSize(1)
            assertThat(producers.first().producerId).isEqualTo(originalId)
        }

        @Test
        fun `should add new producers and keep existing ones`() {
            channelService.createChannel(
                name = "test-channel",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD,
            )
            producerService.createProducer("existing-producer", "test-channel")

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "test-channel",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("key1"),
                            ),
                        ),
                    producers =
                        listOf(
                            ProducerIaC(name = "existing-producer", channelName = "test-channel"),
                            ProducerIaC(name = "new-producer", channelName = "test-channel"),
                        ),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            val producers = producerService.findAll()
            assertThat(producers).hasSize(2)
            assertThat(producers.map { it.name }).containsExactlyInAnyOrder("existing-producer", "new-producer")
        }

        @Test
        fun `should handle producer diff with creates and deletes`() {
            channelService.createChannel(
                name = "test-channel",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD,
            )
            producerService.createProducer("keep-producer", "test-channel")
            producerService.createProducer("delete-producer", "test-channel")

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "test-channel",
                                channelType = "QUEUE",
                                consumptionType = "STANDARD",
                                routingKeys = listOf("key1"),
                            ),
                        ),
                    producers =
                        listOf(
                            ProducerIaC(name = "keep-producer", channelName = "test-channel"),
                            ProducerIaC(name = "new-producer", channelName = "test-channel"),
                        ),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            val producers = producerService.findAll()
            assertThat(producers).hasSize(2)
            assertThat(producers.map { it.name }).containsExactlyInAnyOrder("keep-producer", "new-producer")
            assertThat(producers.map { it.name }).doesNotContain("delete-producer")
        }

        @Test
        fun `should delete producer when its channel is deleted`() {
            channelService.createChannel(
                name = "channel-to-delete",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.STANDARD,
            )
            producerService.createProducer("orphan-producer", "channel-to-delete")

            val iac =
                IaC(
                    channels = emptyList(),
                    producers = emptyList(),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            assertThat(channelService.findAll()).isEmpty()
            assertThat(producerService.findAll()).isEmpty()
        }
    }

    @Nested
    inner class ChannelLinkDiffLogic {
        @Test
        fun `should not recreate existing channel links`() {
            channelService.createChannel(
                name = "source",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
            )
            channelService.createChannel(
                name = "target",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
            )
            val existingLink = channelLinkService.linkChannels("source", "target", "key1", "key1")
            val originalId = existingLink.channelLinkId

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(name = "source", channelType = "QUEUE", consumptionType = "FIFO", routingKeys = listOf("key1")),
                            ChannelIaC(name = "target", channelType = "QUEUE", consumptionType = "FIFO", routingKeys = listOf("key1")),
                        ),
                    producers = emptyList(),
                    channelLinks =
                        listOf(
                            ChannelLinkIaC(
                                sourceChannelName = "source",
                                targetChannelName = "target",
                                sourceRoutingKey = "key1",
                                targetRoutingKey = "key1",
                            ),
                        ),
                )

            iaCService.applyChanges(iac)

            val links = channelLinkService.findAll()
            assertThat(links).hasSize(1)
            assertThat(links.first().channelLinkId).isEqualTo(originalId)
        }

        @Test
        fun `should add new channel links and keep existing ones`() {
            channelService.createChannel(
                name = "source",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1", "key2"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
            )
            channelService.createChannel(
                name = "target",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1", "key2"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
            )
            channelLinkService.linkChannels("source", "target", "key1", "key1")

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "source",
                                channelType = "QUEUE",
                                consumptionType = "FIFO",
                                routingKeys = listOf("key1", "key2"),
                            ),
                            ChannelIaC(
                                name = "target",
                                channelType = "QUEUE",
                                consumptionType = "FIFO",
                                routingKeys = listOf("key1", "key2"),
                            ),
                        ),
                    producers = emptyList(),
                    channelLinks =
                        listOf(
                            ChannelLinkIaC(
                                sourceChannelName = "source",
                                targetChannelName = "target",
                                sourceRoutingKey = "key1",
                                targetRoutingKey = "key1",
                            ),
                            ChannelLinkIaC(
                                sourceChannelName = "source",
                                targetChannelName = "target",
                                sourceRoutingKey = "key2",
                                targetRoutingKey = "key2",
                            ),
                        ),
                )

            iaCService.applyChanges(iac)

            val links = channelLinkService.findAll()
            assertThat(links).hasSize(2)
        }

        @Test
        fun `should handle channel link diff with creates and deletes`() {
            channelService.createChannel(
                name = "source",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1", "key2", "key3"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
            )
            channelService.createChannel(
                name = "target",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1", "key2", "key3"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
            )
            channelLinkService.linkChannels("source", "target", "key1", "key1") // keep
            channelLinkService.linkChannels("source", "target", "key2", "key2") // delete

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(
                                name = "source",
                                channelType = "QUEUE",
                                consumptionType = "FIFO",
                                routingKeys = listOf("key1", "key2", "key3"),
                            ),
                            ChannelIaC(
                                name = "target",
                                channelType = "QUEUE",
                                consumptionType = "FIFO",
                                routingKeys = listOf("key1", "key2", "key3"),
                            ),
                        ),
                    producers = emptyList(),
                    channelLinks =
                        listOf(
                            ChannelLinkIaC(
                                sourceChannelName = "source",
                                targetChannelName = "target",
                                sourceRoutingKey = "key1",
                                targetRoutingKey = "key1",
                            ),
                            ChannelLinkIaC(
                                sourceChannelName = "source",
                                targetChannelName = "target",
                                sourceRoutingKey = "key3",
                                targetRoutingKey = "key3",
                            ),
                        ),
                )

            iaCService.applyChanges(iac)

            val links = channelLinkService.findAll()
            assertThat(links).hasSize(2)
            assertThat(links.map { it.sourceRoutingKey }).containsExactlyInAnyOrder("key1", "key3")
            assertThat(links.map { it.sourceRoutingKey }).doesNotContain("key2")
        }

        @Test
        fun `should delete channel link when source channel is deleted`() {
            channelService.createChannel(
                name = "source-to-delete",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
            )
            channelService.createChannel(
                name = "target",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
            )
            channelLinkService.linkChannels("source-to-delete", "target", "key1", "key1")

            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(name = "target", channelType = "QUEUE", consumptionType = "FIFO", routingKeys = listOf("key1")),
                        ),
                    producers = emptyList(),
                    channelLinks = emptyList(),
                )

            iaCService.applyChanges(iac)

            assertThat(channelLinkService.findAll()).isEmpty()
            assertThat(channelService.findAll()).hasSize(1)
            assertThat(channelService.findAll().first().name).isEqualTo("target")
        }

        @Test
        fun `should identify links by full key including routing keys`() {
            channelService.createChannel(
                name = "source",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("key1"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
            )
            channelService.createChannel(
                name = "target",
                channelType = com.ph.sintropyengine.broker.channel.model.ChannelType.QUEUE,
                routingKeys = listOf("keyA", "keyB"),
                consumptionType = com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO,
            )
            // Link to keyA exists
            channelLinkService.linkChannels("source", "target", "key1", "keyA")

            // IaC defines link to keyB instead
            val iac =
                IaC(
                    channels =
                        listOf(
                            ChannelIaC(name = "source", channelType = "QUEUE", consumptionType = "FIFO", routingKeys = listOf("key1")),
                            ChannelIaC(
                                name = "target",
                                channelType = "QUEUE",
                                consumptionType = "FIFO",
                                routingKeys = listOf("keyA", "keyB"),
                            ),
                        ),
                    producers = emptyList(),
                    channelLinks =
                        listOf(
                            ChannelLinkIaC(
                                sourceChannelName = "source",
                                targetChannelName = "target",
                                sourceRoutingKey = "key1",
                                targetRoutingKey = "keyB",
                            ),
                        ),
                )

            iaCService.applyChanges(iac)

            val links = channelLinkService.findAll()
            assertThat(links).hasSize(1)
            assertThat(links.first().targetRoutingKey).isEqualTo("keyB")
        }
    }

    @Nested
    inner class FileProcessing {
        private fun withTempIaCFile(
            content: String,
            test: (filePath: String) -> Unit,
        ) {
            val tempFile = File.createTempFile("iac-test-", ".json")
            try {
                tempFile.writeText(content)
                test(tempFile.absolutePath)
            } finally {
                tempFile.delete()
            }
        }

        @Test
        fun `should return FILE_NOT_FOUND when file does not exist`() {
            val result = iaCService.processIaCFile("/non/existent/path/init.json")

            assertThat(result).isEqualTo(IaCResult.FILE_NOT_FOUND)
        }

        @Test
        fun `should parse JSON and create resources from file`() {
            val jsonContent =
                """
                {
                    "channels": [
                        {
                            "name": "file-channel",
                            "channelType": "QUEUE",
                            "consumptionType": "STANDARD",
                            "routingKeys": ["key1", "key2"]
                        }
                    ],
                    "producers": [
                        {
                            "name": "file-producer",
                            "channelName": "file-channel"
                        }
                    ],
                    "channelLinks": []
                }
                """.trimIndent()

            withTempIaCFile(jsonContent) { filePath ->
                val result = iaCService.processIaCFile(filePath)

                assertThat(result).isEqualTo(IaCResult.APPLIED)
                assertThat(channelService.findAll()).hasSize(1)
                assertThat(channelService.findAll().first().name).isEqualTo("file-channel")
                assertThat(producerService.findAll()).hasSize(1)
                assertThat(producerService.findAll().first().name).isEqualTo("file-producer")
            }
        }

        @Test
        fun `should store hash in database after processing`() {
            val jsonContent =
                """
                {
                    "channels": [
                        {
                            "name": "hash-test-channel",
                            "channelType": "QUEUE",
                            "consumptionType": "STANDARD",
                            "routingKeys": ["key1"]
                        }
                    ],
                    "producers": [],
                    "channelLinks": []
                }
                """.trimIndent()

            withTempIaCFile(jsonContent) { filePath ->
                iaCService.processIaCFile(filePath)

                val storedFile = iaCRepository.findByFileName("init.json")
                assertThat(storedFile).isNotNull
                assertThat(storedFile?.hash).isNotBlank()
            }
        }

        @Test
        fun `should return UNCHANGED when file hash matches stored hash`() {
            val jsonContent =
                """
                {
                    "channels": [
                        {
                            "name": "unchanged-channel",
                            "channelType": "QUEUE",
                            "consumptionType": "STANDARD",
                            "routingKeys": ["key1"]
                        }
                    ],
                    "producers": [],
                    "channelLinks": []
                }
                """.trimIndent()

            withTempIaCFile(jsonContent) { filePath ->
                // First call - should apply
                val firstResult = iaCService.processIaCFile(filePath)
                assertThat(firstResult).isEqualTo(IaCResult.APPLIED)

                // Second call with same content - should be unchanged
                val secondResult = iaCService.processIaCFile(filePath)
                assertThat(secondResult).isEqualTo(IaCResult.UNCHANGED)
            }
        }

        @Test
        fun `should apply changes when file content changes`() {
            val initialContent =
                """
                {
                    "channels": [
                        {
                            "name": "initial-channel",
                            "channelType": "QUEUE",
                            "consumptionType": "STANDARD",
                            "routingKeys": ["key1"]
                        }
                    ],
                    "producers": [],
                    "channelLinks": []
                }
                """.trimIndent()

            val updatedContent =
                """
                {
                    "channels": [
                        {
                            "name": "updated-channel",
                            "channelType": "QUEUE",
                            "consumptionType": "STANDARD",
                            "routingKeys": ["key1"]
                        }
                    ],
                    "producers": [],
                    "channelLinks": []
                }
                """.trimIndent()

            val tempFile = File.createTempFile("iac-test-", ".json")
            try {
                // First apply
                tempFile.writeText(initialContent)
                val firstResult = iaCService.processIaCFile(tempFile.absolutePath)
                assertThat(firstResult).isEqualTo(IaCResult.APPLIED)
                assertThat(channelService.findAll().first().name).isEqualTo("initial-channel")

                // Update file content
                tempFile.writeText(updatedContent)
                val secondResult = iaCService.processIaCFile(tempFile.absolutePath)
                assertThat(secondResult).isEqualTo(IaCResult.APPLIED)

                val channels = channelService.findAll()
                assertThat(channels).hasSize(1)
                assertThat(channels.first().name).isEqualTo("updated-channel")
            } finally {
                tempFile.delete()
            }
        }

        @Test
        fun `should update hash in database when file changes`() {
            val content1 =
                """
                {
                    "channels": [{"name": "ch1", "channelType": "QUEUE", "consumptionType": "STANDARD", "routingKeys": ["k1"]}],
                    "producers": [],
                    "channelLinks": []
                }
                """.trimIndent()

            val content2 =
                """
                {
                    "channels": [{"name": "ch2", "channelType": "QUEUE", "consumptionType": "STANDARD", "routingKeys": ["k1"]}],
                    "producers": [],
                    "channelLinks": []
                }
                """.trimIndent()

            val tempFile = File.createTempFile("iac-test-", ".json")
            try {
                tempFile.writeText(content1)
                iaCService.processIaCFile(tempFile.absolutePath)
                val hash1 = iaCRepository.findByFileName("init.json")?.hash

                tempFile.writeText(content2)
                iaCService.processIaCFile(tempFile.absolutePath)
                val hash2 = iaCRepository.findByFileName("init.json")?.hash

                assertThat(hash1).isNotEqualTo(hash2)
            } finally {
                tempFile.delete()
            }
        }

        @Test
        fun `should create full infrastructure from JSON file`() {
            val jsonContent =
                """
                {
                    "channels": [
                        {
                            "name": "orders",
                            "channelType": "QUEUE",
                            "consumptionType": "FIFO",
                            "routingKeys": ["new", "processed"]
                        },
                        {
                            "name": "notifications",
                            "channelType": "QUEUE",
                            "consumptionType": "FIFO",
                            "routingKeys": ["email"]
                        }
                    ],
                    "producers": [
                        {"name": "order-producer", "channelName": "orders"}
                    ],
                    "channelLinks": [
                        {
                            "sourceChannelName": "orders",
                            "targetChannelName": "notifications",
                            "sourceRoutingKey": "processed",
                            "targetRoutingKey": "email"
                        }
                    ]
                }
                """.trimIndent()

            withTempIaCFile(jsonContent) { filePath ->
                val result = iaCService.processIaCFile(filePath)

                assertThat(result).isEqualTo(IaCResult.APPLIED)
                assertThat(channelService.findAll()).hasSize(2)
                assertThat(producerService.findAll()).hasSize(1)
                assertThat(channelLinkService.findAll()).hasSize(1)

                val link = channelLinkService.findAll().first()
                assertThat(link.sourceRoutingKey).isEqualTo("processed")
                assertThat(link.targetRoutingKey).isEqualTo("email")
            }
        }

        @Test
        fun `should handle empty IaC file`() {
            val jsonContent =
                """
                {
                    "channels": [],
                    "producers": [],
                    "channelLinks": []
                }
                """.trimIndent()

            withTempIaCFile(jsonContent) { filePath ->
                val result = iaCService.processIaCFile(filePath)

                assertThat(result).isEqualTo(IaCResult.APPLIED)
                assertThat(channelService.findAll()).isEmpty()
                assertThat(producerService.findAll()).isEmpty()
                assertThat(channelLinkService.findAll()).isEmpty()
            }
        }

        @Test
        fun `should delete existing resources when processing empty file after populated file`() {
            val populatedContent =
                """
                {
                    "channels": [{"name": "to-delete", "channelType": "QUEUE", "consumptionType": "STANDARD", "routingKeys": ["k1"]}],
                    "producers": [{"name": "producer-to-delete", "channelName": "to-delete"}],
                    "channelLinks": []
                }
                """.trimIndent()

            val emptyContent =
                """
                {
                    "channels": [],
                    "producers": [],
                    "channelLinks": []
                }
                """.trimIndent()

            val tempFile = File.createTempFile("iac-test-", ".json")
            try {
                // First apply with resources
                tempFile.writeText(populatedContent)
                iaCService.processIaCFile(tempFile.absolutePath)
                assertThat(channelService.findAll()).hasSize(1)
                assertThat(producerService.findAll()).hasSize(1)

                // Then apply empty - should delete everything
                tempFile.writeText(emptyContent)
                iaCService.processIaCFile(tempFile.absolutePath)
                assertThat(channelService.findAll()).isEmpty()
                assertThat(producerService.findAll()).isEmpty()
            } finally {
                tempFile.delete()
            }
        }
    }
}
