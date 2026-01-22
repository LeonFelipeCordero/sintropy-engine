package com.ph.sintropyengine.broker.channel.service

import com.ph.sintropyengine.broker.channel.model.Channel
import com.ph.sintropyengine.broker.channel.model.ChannelLink
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.repository.ChannelLinkRepository
import com.ph.sintropyengine.broker.channel.repository.ChannelRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class ChannelLinkService(
    private val channelLinkRepository: ChannelLinkRepository,
    private val channelRepository: ChannelRepository,
    private val channelService: ChannelService,
) {
    @Transactional
    fun linkChannels(
        sourceChannelName: String,
        targetChannelName: String,
        sourceRoutingKey: String,
        targetRoutingKey: String,
    ): ChannelLink {
        val sourceChannel = channelService.findByNameAndRoutingKeyStrict(sourceChannelName, sourceRoutingKey)
        val targetChannel = channelService.findByNameAndRoutingKeyStrict(targetChannelName, targetRoutingKey)

        validateLinkCompatibility(sourceChannel, targetChannel)

        val channelLink =
            ChannelLink(
                sourceChannelId = sourceChannel.channelId!!,
                targetChannelId = targetChannel.channelId!!,
                sourceRoutingKey = sourceRoutingKey,
                targetRoutingKey = targetRoutingKey,
            )

        return channelLinkRepository.save(channelLink)
    }

    fun findById(channelLinkId: Long): ChannelLink? = channelLinkRepository.findById(channelLinkId)

    fun findByUUID(channelLinkUUID: UUID): ChannelLink? = channelLinkRepository.findByUUID(channelLinkUUID)

    fun findAll(): List<ChannelLink> = channelLinkRepository.findAll()

    fun getLinksFromChannel(channelName: String): List<ChannelLink> {
        val channel =
            channelRepository.findByName(channelName)
                ?: throw IllegalStateException("Channel with name $channelName not found")

        return channelLinkRepository.findBySourceChannelId(channel.channelId!!)
    }

    fun getLinksToChannel(channelName: String): List<ChannelLink> {
        val channel =
            channelRepository.findByName(channelName)
                ?: throw IllegalStateException("Channel with name $channelName not found")

        return channelLinkRepository.findByTargetChannelId(channel.channelId!!)
    }

    @Transactional
    fun unlinkChannels(channelLinkUUID: UUID) {
        val channelLink = channelLinkRepository.findByUUID(channelLinkUUID)
            ?: throw IllegalStateException("Channel link with uuid $channelLinkUUID not found")

        channelLinkRepository.delete(channelLink.channelLinkId!!)
    }

    @Transactional
    fun enableLink(channelLinkUUID: UUID) {
        val channelLink = channelLinkRepository.findByUUID(channelLinkUUID)
            ?: throw IllegalStateException("Channel link with id $channelLinkUUID not found")

        channelLinkRepository.setEnabled(channelLink.channelLinkId!!, true)
    }

    @Transactional
    fun disableLink(channelLinkUUID: UUID) {
        val channelLink = channelLinkRepository.findByUUID(channelLinkUUID)
            ?: throw IllegalStateException("Channel link with id $channelLinkUUID not found")

        channelLinkRepository.setEnabled(channelLink.channelLinkId!!, false)
    }

    private fun validateLinkCompatibility(
        source: Channel,
        target: Channel,
    ) {
        val sourceIsStandard = source.consumptionType == ConsumptionType.STANDARD

        val targetRequiresOrdering =
            target.channelType == ChannelType.STREAM || target.consumptionType == ConsumptionType.FIFO

        if (sourceIsStandard && targetRequiresOrdering) {
            val targetType =
                if (target.channelType == ChannelType.STREAM) {
                    "Stream"
                } else {
                    "FIFO Queue"
                }
            throw IllegalStateException(
                "Cannot link Standard Queue to $targetType: Standard Queues don't guarantee message ordering",
            )
        }
    }
}
