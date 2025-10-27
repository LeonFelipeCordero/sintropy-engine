package com.ph.sintropyengine.broker.service

import com.ph.sintropyengine.broker.model.Channel
import com.ph.sintropyengine.broker.model.ChannelType
import com.ph.sintropyengine.broker.repository.ChannelRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class ChannelService(
    private val channelRepository: ChannelRepository,
) {

    @Transactional
    fun createChannel(name: String, channelType: ChannelType, routingKeys: List<String>): Channel {
        require(routingKeys.isNotEmpty()) { "At least one routing key must be provided" }

        channelRepository.findByName(name)
            ?.let { throw IllegalStateException("Channel with name $name already exists") }

        val channel = Channel(
            name = name,
            channelType = channelType,
            routingKeys = routingKeys.toMutableList(),
            consumers = emptyList()
        )
        return channelRepository.save(channel)
    }

    fun findById(channelId: UUID): Channel? = channelRepository.findById(channelId)

    fun findByIdName(name: String): Channel? = channelRepository.findByName(name)

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