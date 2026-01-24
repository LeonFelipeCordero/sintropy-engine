package com.ph.sintropyengine.broker.channel.service

import com.ph.sintropyengine.broker.channel.model.Channel
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.repository.ChannelRepository
import com.ph.sintropyengine.broker.shared.utils.validForName
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class ChannelService(
    private val channelRepository: ChannelRepository,
) {
    @Transactional
    fun createChannel(
        name: String,
        channelType: ChannelType,
        routingKeys: List<String>,
        consumptionType: ConsumptionType? = null,
    ): Channel {
        require(routingKeys.isNotEmpty()) { "At least one routing key must be provided" }
        require(name.validForName()) { "Channel name must not contain white spaces" }

        channelRepository
            .findByName(name)
            ?.let { throw IllegalStateException("Channel with name $name already exists") }

        val channel =
            Channel(
                name = name,
                channelType = channelType,
                routingKeys = routingKeys.toMutableList(),
                consumptionType = consumptionType,
            )

        logger.info { "Created channel ${channel.logging()}" }

        return channelRepository.save(channel)
    }

    fun findById(channelId: UUID): Channel? = channelRepository.findById(channelId)

    fun findByIds(ids: Set<UUID>): Map<UUID, Channel> = channelRepository.findByIds(ids)

    fun findByName(name: String): Channel? = channelRepository.findByName(name)

    fun findByNameAndRoutingKeyStrict(
        name: String,
        routingKey: String,
    ): Channel =
        channelRepository.findByNameAndRoutingKey(name, routingKey)
            ?: throw IllegalStateException("Channel with name $name and routing key $routingKey not found")

    fun findAll(): List<Channel> = channelRepository.findAll()

    @Transactional
    fun deleteByName(name: String) {
        val channel =
            channelRepository.findByName(name)
                ?: throw IllegalStateException("Channel with name $name not found")
        channelRepository.delete(channel.channelId!!)
    }

    @Transactional
    fun deleteChannel(id: UUID) {
        channelRepository.findById(id) ?: throw IllegalStateException("Channel with id $id not found")

        channelRepository.delete(id)
    }

    @Transactional
    fun addRoutingKeyByName(
        channelName: String,
        routingKey: String,
    ) {
        val channel =
            channelRepository.findByName(channelName)
                ?: throw IllegalStateException("Channel with name $channelName not found")

        if (channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("RoutingKey $routingKey already exists")
        }

        channelRepository.addRoutingKey(channel.channelId!!, routingKey)
    }
}
