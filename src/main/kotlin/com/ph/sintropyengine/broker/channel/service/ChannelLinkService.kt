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
) {
    @Transactional
    fun linkChannels(
        sourceChannelName: String,
        targetChannelName: String,
        sourceRoutingKey: String,
        targetRoutingKey: String,
    ): ChannelLink {
        val sourceChannel =
            channelRepository.findByName(sourceChannelName)
                ?: throw IllegalStateException("Source channel with name $sourceChannelName not found")

        val targetChannel =
            channelRepository.findByName(targetChannelName)
                ?: throw IllegalStateException("Target channel with name $targetChannelName not found")

        if (!sourceChannel.containsRoutingKey(sourceRoutingKey)) {
            throw IllegalStateException("Source routing key $sourceRoutingKey does not exist in channel $sourceChannelName")
        }

        if (!targetChannel.containsRoutingKey(targetRoutingKey)) {
            throw IllegalStateException("Target routing key $targetRoutingKey does not exist in channel $targetChannelName")
        }

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

    fun findById(channelLinkId: UUID): ChannelLink? = channelLinkRepository.findById(channelLinkId)

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
    fun unlinkChannels(channelLinkId: UUID) {
        channelLinkRepository.findById(channelLinkId)
            ?: throw IllegalStateException("Channel link with id $channelLinkId not found")

        channelLinkRepository.delete(channelLinkId)
    }

    @Transactional
    fun enableLink(channelLinkId: UUID) {
        channelLinkRepository.findById(channelLinkId)
            ?: throw IllegalStateException("Channel link with id $channelLinkId not found")

        channelLinkRepository.setEnabled(channelLinkId, true)
    }

    @Transactional
    fun disableLink(channelLinkId: UUID) {
        channelLinkRepository.findById(channelLinkId)
            ?: throw IllegalStateException("Channel link with id $channelLinkId not found")

        channelLinkRepository.setEnabled(channelLinkId, false)
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
