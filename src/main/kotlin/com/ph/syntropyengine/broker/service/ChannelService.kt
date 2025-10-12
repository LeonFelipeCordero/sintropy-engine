package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.broker.model.Channel
import com.ph.syntropyengine.broker.model.Consumer
import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.repository.ChannelRepository
import com.ph.syntropyengine.broker.repository.MessageRepository
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class ChannelService(
    private val channelRepository: ChannelRepository,
    private val messageRepository: MessageRepository
) {

    fun createChannel(name: String, routingKeys: List<String>): Channel {
        require(routingKeys.isNotEmpty()) { "At least one routing key must be provided" }

        channelRepository.findByName(name)
            ?.let { throw IllegalStateException("Channel with name $name already exists") }

        val channel = Channel(name = name, routingKeys = routingKeys.toMutableList(), consumers = emptyList())
        return channelRepository.save(channel)
    }

    fun findById(channelId: UUID): Channel? = channelRepository.findById(channelId)

    fun findByIdName(name: String): Channel? = channelRepository.findByName(name)

    fun deleteChannel(id: UUID) {
        channelRepository.findById(id) ?: throw IllegalStateException("Channel with id $id not found")

        channelRepository.delete(id)
    }

    fun addRoutingKey(id: UUID, routingKey: String) {
        val channel = channelRepository.findById(id) ?: throw IllegalStateException("Channel with id $id not found")

        if (channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("RoutingKey $routingKey already exists")
        }

        channelRepository.addRoutingKey(channel.channelId!!, routingKey)
    }

    fun publish(message: Message): Message {
        val channel = findById(message.channelId)
            ?: throw IllegalStateException("Channel ${message.channelId} not found")

        if (!channel.containsRoutingKey(message.routingKey)) {
            throw IllegalArgumentException("Channel doesn't have routing-key ${message.routingKey}")
        }

        return messageRepository.save(message)
    }

    fun consume(consumer: Consumer, routingKey: String) {

    }
}