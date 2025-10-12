package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.model.Producer
import com.ph.syntropyengine.broker.repository.ProducerRepository
import java.lang.IllegalStateException
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class ProducerService(
    private val channelService: ChannelService,
    private val producerRepository: ProducerRepository
) {

    fun createProducer(name: String, channelName: String): Producer {
        val channel =
            channelService.findByIdName(channelName) ?: throw IllegalStateException("Channel $channelName not found")

        val producer = Producer(name = name, channelId = channel.channelId!!)
        return producerRepository.save(producer)
    }

    fun findById(consumerId: UUID): Producer? = producerRepository.findById(consumerId)

    fun findByChannel(channelName: String): List<Producer> =
        channelService.findByIdName(channelName)?.let {
            producerRepository.findByChannel(channelName)
        } ?: throw IllegalStateException("Channel $channelName not found")

    fun deleteProducer(producerId: UUID) =
        producerRepository.findById(producerId)?.let {
            producerRepository.delete(producerId)
        } ?: throw IllegalStateException("Consumer $producerId not found")

    fun publishMessage(message: Message): Message {
        val channel = channelService.findById(message.channelId)
            ?: throw IllegalStateException("Channel ${message.channelId} not found")

        if (!channel.containsRoutingKey(message.routingKey)) {
            throw IllegalStateException(
                "Channel ${message.channelId} does not have routing-key ${message.routingKey}"
            )
        }

        return channelService.publish(message)
    }
}