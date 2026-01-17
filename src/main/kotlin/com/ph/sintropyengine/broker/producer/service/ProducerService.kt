package com.ph.sintropyengine.broker.producer.service

import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.model.MessagePreStore
import com.ph.sintropyengine.broker.consumption.model.MessageStatus
import com.ph.sintropyengine.broker.consumption.repository.CircuitBreakerRepository
import com.ph.sintropyengine.broker.consumption.repository.DeadLetterQueueRepository
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.producer.api.PublishMessageRequest
import com.ph.sintropyengine.broker.producer.model.Producer
import com.ph.sintropyengine.broker.producer.repository.ProducerRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.lang.IllegalStateException
import java.util.UUID

@ApplicationScoped
class ProducerService(
    private val channelService: ChannelService,
    private val producerRepository: ProducerRepository,
    private val messageRepository: MessageRepository,
    private val circuitBreakerRepository: CircuitBreakerRepository,
    private val deadLetterQueueRepository: DeadLetterQueueRepository,
) {
    @Transactional
    fun createProducer(
        name: String,
        channelName: String,
    ): Producer {
        val channel =
            channelService.findByName(channelName) ?: throw IllegalStateException("Channel $channelName not found")

        val producer = Producer(name = name, channelId = channel.channelId!!)
        return producerRepository.save(producer)
    }

    fun findById(consumerId: UUID): Producer? = producerRepository.findById(consumerId)

    fun findByName(name: String): Producer? = producerRepository.findByName(name)

    fun findAll(): List<Producer> = producerRepository.findAll()

    fun findByChannel(channelName: String): List<Producer> =
        channelService.findByName(channelName)?.let {
            producerRepository.findByChannel(channelName)
        } ?: throw IllegalStateException("Channel $channelName not found")

    @Transactional
    fun deleteProducer(producerId: UUID) =
        producerRepository.findById(producerId)?.let {
            producerRepository.delete(producerId)
        } ?: throw IllegalStateException("Producer $producerId not found")

    @Transactional
    fun deleteByName(name: String) =
        producerRepository.findByName(name)?.let {
            producerRepository.deleteByName(name)
        } ?: throw IllegalStateException("Producer $name not found")

    // TODO: Should get the preStoreMessage
    @Transactional
    fun publishMessage(request: PublishMessageRequest): Message {
        val channel = channelService.findByNameAndRoutingKeyStrict(request.channelName, request.routingKey)

        val producer =
            producerRepository.findByName(request.producerName)
                ?: throw IllegalStateException("Producer ${request.producerName} not found")

        if (producer.channelId != channel.channelId) {
            throw IllegalStateException(
                "Producer ${request.producerName} is not linked to channel ${request.channelName}",
            )
        }

        val messagePreStore =
            MessagePreStore(
                channelId = channel.channelId,
                producerId = producer.producerId!!,
                routingKey = request.routingKey,
                message = request.message,
                headers = request.headers,
                originMessageId = null,
            )

        if (!channel.canWriteMessage(messagePreStore.routingKey)) {
            val dlqMessage = deadLetterQueueRepository.save(messagePreStore)
            return Message(
                messageId = dlqMessage.messageId,
                timestamp = dlqMessage.timestamp,
                channelId = dlqMessage.channelId,
                producerId = dlqMessage.producerId,
                routingKey = dlqMessage.routingKey,
                message = dlqMessage.message,
                headers = dlqMessage.headers,
                status = MessageStatus.FAILED,
                originMessageId = dlqMessage.originMessageId,
                deliveredTimes = dlqMessage.deliveredTimes,
            )
        }

        return messageRepository.save(messagePreStore)
    }
}
