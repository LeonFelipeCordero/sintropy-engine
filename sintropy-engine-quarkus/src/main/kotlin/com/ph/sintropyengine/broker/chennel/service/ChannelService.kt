package com.ph.sintropyengine.broker.chennel.service

import com.ph.sintropyengine.broker.chennel.model.Channel
import com.ph.sintropyengine.broker.chennel.model.ChannelType
import com.ph.sintropyengine.broker.chennel.model.ConsumptionType
import com.ph.sintropyengine.broker.chennel.repository.ChannelRepository
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
        consumptionType: ConsumptionType? = null
    ): Channel {
        require(routingKeys.isNotEmpty()) { "At least one routing key must be provided" }

        channelRepository.findByName(name)
            ?.let { throw IllegalStateException("Channel with name $name already exists") }

        val channel = Channel(
            name = name,
            channelType = channelType,
            routingKeys = routingKeys.toMutableList(),
            consumptionType = consumptionType
        )
        return channelRepository.save(channel)
    }

    fun findById(channelId: UUID): Channel? = channelRepository.findById(channelId)

    fun findByName(name: String): Channel? = channelRepository.findByName(name)

    @Transactional
    fun deleteChannel(id: UUID) {
        channelRepository.findById(id) ?: throw IllegalStateException("Channel with id $id not found")

        channelRepository.delete(id)
    }

    @Transactional
    fun addRoutingKey(id: UUID, routingKey: String) {
        val channel = channelRepository.findById(id) ?: throw IllegalStateException("Channel with id $id not found")

        if (channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("RoutingKey $routingKey already exists")
        }

        channelRepository.addRoutingKey(channel.channelId!!, routingKey)
    }
}