package com.ph.sintropyengine.broker.service

import com.ph.sintropyengine.broker.model.Message
import com.ph.sintropyengine.broker.model.Producer
import com.ph.sintropyengine.broker.repository.MessageRepository
import com.ph.sintropyengine.broker.repository.ProducerRepository
import com.ph.sintropyengine.broker.resource.PublishMessageRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.lang.IllegalStateException
import java.util.UUID
import org.jooq.JSONB

@ApplicationScoped
class ProducerService(
    private val channelService: ChannelService,
    private val producerRepository: ProducerRepository,
    private val messageRepository: MessageRepository
) {

    @Transactional
    fun createProducer(name: String, channelName: String): Producer {
        val channel =
            channelService.findByName(channelName) ?: throw IllegalStateException("Channel $channelName not found")

        val producer = Producer(name = name, channelId = channel.channelId!!)
        return producerRepository.save(producer)
    }

    fun findById(consumerId: UUID): Producer? = producerRepository.findById(consumerId)

    fun findByChannel(channelName: String): List<Producer> =
        channelService.findByName(channelName)?.let {
            producerRepository.findByChannel(channelName)
        } ?: throw IllegalStateException("Channel $channelName not found")

    @Transactional
    fun deleteProducer(producerId: UUID) =
        producerRepository.findById(producerId)?.let {
            producerRepository.delete(producerId)
        } ?: throw IllegalStateException("Consumer $producerId not found")

    @Transactional
    fun publishMessage(request: PublishMessageRequest): Message {
        val channel = channelService.findByName(request.channelName)
            ?: throw IllegalStateException("Channel ${request.channelName} not found")

        val producer = producerRepository.findByName(request.producerName)
            ?: throw IllegalStateException("Producer ${request.producerName} not found")

        if (!channel.containsRoutingKey(request.routingKey)) {
            throw IllegalStateException(
                "Channel ${request.channelName} does not have routing-key ${request.routingKey}"
            )
        }

        val message = Message(
            messageId = request.messageId,
            channelId = channel.channelId!!,
            producerId = producer.producerId!!,
            routingKey = request.routingKey,
            message = JSONB.jsonb(request.message),
            headers = JSONB.jsonb(request.headers)
        )
        return messageRepository.save(message)
    }
}