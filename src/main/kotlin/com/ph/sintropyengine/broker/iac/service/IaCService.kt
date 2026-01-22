package com.ph.sintropyengine.broker.iac.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.service.ChannelLinkService
import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.iac.model.ChannelIaC
import com.ph.sintropyengine.broker.iac.model.ChannelLinkIaC
import com.ph.sintropyengine.broker.iac.model.IaC
import com.ph.sintropyengine.broker.iac.model.ProducerIaC
import com.ph.sintropyengine.broker.iac.repository.IaCRepository
import com.ph.sintropyengine.broker.producer.service.ProducerService
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.io.File
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

private const val IAC_FILE_NAME = "init.json"

@Startup
@ApplicationScoped
class IaCService(
    private val objectMapper: ObjectMapper,
    private val channelService: ChannelService,
    private val producerService: ProducerService,
    private val channelLinkService: ChannelLinkService,
    private val iaCRepository: IaCRepository,
) {
    @PostConstruct
    fun init() {
        processIaCFile()
    }

    /**
     * Processes IaC file from the default location ($HOME/.sintropy-engine/init.json).
     * Returns result indicating what action was taken.
     */
    @Transactional
    internal fun processIaCFile(basePath: String? = null): IaCResult {
        val userHome =
            System.getProperty("user.home")
                ?: throw IllegalStateException("Home directory not found for IaC file")

        val filePath = basePath ?: "$userHome/.sintropy-engine/$IAC_FILE_NAME"
        val file = File(filePath)

        if (!file.exists()) {
            logger.info { "IaC file not found at ${file.absolutePath}. Skipping IaC initialization..." }
            return IaCResult.FILE_NOT_FOUND
        }

        val fileContent = file.readText()
        val currentHash = calculateHash(fileContent)

        val storedFile = iaCRepository.findByFileName(IAC_FILE_NAME)

        if (storedFile != null && storedFile.hash == currentHash) {
            logger.info { "IaC file unchanged (hash: $currentHash). Skipping initialization..." }
            return IaCResult.UNCHANGED
        }

        logger.info { "IaC file changed. Processing changes..." }

        val iac = objectMapper.readValue(fileContent, IaC::class.java)

        applyChanges(iac)

        if (storedFile == null) {
            iaCRepository.save(IAC_FILE_NAME, currentHash)
        } else {
            iaCRepository.updateHash(IAC_FILE_NAME, currentHash)
        }

        logger.info { "IaC initialization completed. Hash: $currentHash" }
        return IaCResult.APPLIED
    }

    internal fun applyChanges(desired: IaC) {
        val existingChannels = channelService.findAll()
        val existingProducers = producerService.findAll()
        val existingLinks = channelLinkService.findAll()

        val desiredChannelNames = desired.channels.map { it.name }.toSet()
        val desiredProducerNames = desired.producers.map { it.name }.toSet()
        val desiredLinkKeys = desired.channelLinks.map { linkKey(it) }.toSet()

        existingLinks
            .filter { link ->
                val sourceChannel = existingChannels.find { it.channelId == link.sourceChannelId }
                val targetChannel = existingChannels.find { it.channelId == link.targetChannelId }
                if (sourceChannel == null || targetChannel == null) {
                    true // Delete orphaned links
                } else {
                    val key =
                        linkKey(
                            sourceChannelName = sourceChannel.name,
                            targetChannelName = targetChannel.name,
                            sourceRoutingKey = link.sourceRoutingKey,
                            targetRoutingKey = link.targetRoutingKey,
                        )
                    key !in desiredLinkKeys
                }
            }.forEach { link ->
                logger.info { "Deleting channel link ${link.channelLinkId}" }
                channelLinkService.unlinkChannels(link.channelLinkUuid!!)
            }

        existingProducers
            .filter { it.name !in desiredProducerNames }
            .forEach { producer ->
                logger.info { "Deleting producer ${producer.name}" }
                producerService.deleteByName(producer.name)
            }

        existingChannels
            .filter { it.name !in desiredChannelNames }
            .forEach { channel ->
                logger.info { "Deleting channel ${channel.name}" }
                channelService.deleteByName(channel.name)
            }

        val existingChannelNames = existingChannels.map { it.name }.toSet()
        desired.channels
            .filter { it.name !in existingChannelNames }
            .forEach { channelIaC ->
                createChannel(channelIaC)
            }

        val existingProducerNames = existingProducers.map { it.name }.toSet()
        desired.producers
            .filter { it.name !in existingProducerNames }
            .forEach { producerIaC ->
                createProducer(producerIaC)
            }

        val existingLinkKeys =
            existingLinks
                .mapNotNull { link ->
                    val sourceChannel = existingChannels.find { it.channelId == link.sourceChannelId }
                    val targetChannel = existingChannels.find { it.channelId == link.targetChannelId }
                    if (sourceChannel != null && targetChannel != null) {
                        linkKey(
                            sourceChannelName = sourceChannel.name,
                            targetChannelName = targetChannel.name,
                            sourceRoutingKey = link.sourceRoutingKey,
                            targetRoutingKey = link.targetRoutingKey,
                        )
                    } else {
                        null
                    }
                }.toSet()

        desired.channelLinks
            .filter { linkKey(it) !in existingLinkKeys }
            .forEach { channelLinkIaC ->
                createChannelLink(channelLinkIaC)
            }
    }

    private fun createChannel(channelIaC: ChannelIaC) {
        val channel =
            channelService.createChannel(
                name = channelIaC.name,
                channelType = ChannelType.valueOf(channelIaC.channelType),
                routingKeys = channelIaC.routingKeys,
                consumptionType = channelIaC.consumptionType?.let { ConsumptionType.valueOf(it) },
            )
        channel.routingKeys.forEach { routingKey ->
            logger.info { "Created channel ${channel.name} with routing ${channel.routing(routingKey)}" }
        }
    }

    private fun createProducer(producerIaC: ProducerIaC) {
        val producer =
            producerService.createProducer(
                name = producerIaC.name,
                channelName = producerIaC.channelName,
            )
        logger.info { "Created producer ${producer.name} with id ${producer.producerId}" }
    }

    private fun createChannelLink(channelLinkIaC: ChannelLinkIaC) {
        val channelLink =
            channelLinkService.linkChannels(
                sourceChannelName = channelLinkIaC.sourceChannelName,
                targetChannelName = channelLinkIaC.targetChannelName,
                sourceRoutingKey = channelLinkIaC.sourceRoutingKey,
                targetRoutingKey = channelLinkIaC.targetRoutingKey,
            )
        logger.info {
            "Created channel link between ${
                routing(channelLink.sourceChannelId, channelLink.sourceRoutingKey)
            } to ${
                routing(channelLink.targetChannelId, channelLink.targetRoutingKey)
            }"
        }
    }

    private fun calculateHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun linkKey(link: ChannelLinkIaC): String =
        linkKey(
            sourceChannelName = link.sourceChannelName,
            targetChannelName = link.targetChannelName,
            sourceRoutingKey = link.sourceRoutingKey,
            targetRoutingKey = link.targetRoutingKey,
        )

    private fun linkKey(
        sourceChannelName: String,
        targetChannelName: String,
        sourceRoutingKey: String,
        targetRoutingKey: String,
    ): String = "$sourceChannelName:$sourceRoutingKey->$targetChannelName:$targetRoutingKey"
}

enum class IaCResult {
    FILE_NOT_FOUND,
    UNCHANGED,
    APPLIED,
}
