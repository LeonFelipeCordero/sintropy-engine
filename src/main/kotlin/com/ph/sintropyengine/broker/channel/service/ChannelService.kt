package com.ph.sintropyengine.broker.channel.service

import com.ph.sintropyengine.broker.channel.model.Channel
import com.ph.sintropyengine.broker.channel.model.ChannelType
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.repository.ChannelRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

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
        return channelRepository.save(channel)
    }

    fun findById(channelId: UUID): Channel? = channelRepository.findById(channelId)

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
    fun addRoutingKey(
        id: UUID,
        routingKey: String,
    ) {
        val channel = channelRepository.findById(id) ?: throw IllegalStateException("Channel with id $id not found")

        if (channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("RoutingKey $routingKey already exists")
        }

        channelRepository.addRoutingKey(channel.channelId!!, routingKey)
    }
}
