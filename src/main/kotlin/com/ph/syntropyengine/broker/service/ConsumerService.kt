package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.broker.model.Consumer
import com.ph.syntropyengine.broker.repository.ConsumerRepository
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class ConsumerService(
    private val channelService: ChannelService,
    private val consumerRepository: ConsumerRepository
) {

    fun createConsumer(channelName: String, routingKey: String): Consumer {
        val channel =
            channelService.findByIdName(channelName) ?: throw IllegalStateException("Channel $channelName not found")

        if (!channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("Routing key $routingKey not found in channel $channelName")
        }

        val consumer = Consumer(channelId = channel.channelId!!, routingKey = routingKey)
        return consumerRepository.save(consumer)
    }

    fun findById(consumerId: UUID): Consumer? = consumerRepository.findById(consumerId)

    fun findByChannel(channelName: String): List<Consumer> =
        channelService.findByIdName(channelName)?.let {
            consumerRepository.findByChannel(channelName)
        } ?: throw IllegalStateException("Channel $channelName not found")

    fun deleteConsumer(consumerId: UUID) =
        consumerRepository.findById(consumerId)?.let {
            consumerRepository.delete(consumerId)
        } ?: throw IllegalStateException("Consumer $consumerId not found")

}