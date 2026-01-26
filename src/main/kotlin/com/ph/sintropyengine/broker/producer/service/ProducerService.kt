package com.ph.sintropyengine.broker.producer.service

import com.ph.sintropyengine.broker.channel.service.ChannelService
import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.model.MessagePreStore
import com.ph.sintropyengine.broker.consumption.model.MessageStatus
import com.ph.sintropyengine.broker.consumption.repository.DeadLetterQueueRepository
import com.ph.sintropyengine.broker.consumption.repository.MessageRepository
import com.ph.sintropyengine.broker.producer.model.Producer
import com.ph.sintropyengine.broker.producer.repository.ProducerRepository
import com.ph.sintropyengine.broker.shared.utils.Patterns.routing
import com.ph.sintropyengine.broker.shared.utils.validForName
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.lang.IllegalStateException
import java.util.UUID


private val logger = KotlinLogging.logger {}

@ApplicationScoped
class ProducerService(
    private val channelService: ChannelService,
    private val producerRepository: ProducerRepository,
    private val messageRepository: MessageRepository,
    private val deadLetterQueueRepository: DeadLetterQueueRepository,
) {
    @Transactional
    fun createProducer(name: String): Producer {
        require(name.validForName()) { "Producer name must not contain white spaces" }

        val producer = Producer(name = name)
        return producerRepository.save(producer)
    }

    fun findById(consumerId: UUID): Producer? = producerRepository.findById(consumerId)

    fun findByIds(ids: Set<UUID>): Map<UUID, Producer> = producerRepository.findByIds(ids)

    fun findByName(name: String): Producer? = producerRepository.findByName(name)

    fun findAll(): List<Producer> = producerRepository.findAll()

    @Transactional
    fun deleteByName(name: String) =
        producerRepository.findByName(name)?.let {
            producerRepository.deleteByName(name)
        } ?: throw IllegalStateException("Producer $name not found")

    // TODO: Move this to a message service
    @Transactional
    fun publishMessage(messagePreStore: MessagePreStore): Message {
        val channel =
            channelService.findByNameAndRoutingKeyStrict(messagePreStore.channelName, messagePreStore.routingKey)

        val producer =
            producerRepository.findByName(messagePreStore.producerName)
                ?: throw IllegalStateException("Producer ${messagePreStore.producerName} not found")

        if (!channel.canWriteMessage(messagePreStore.routingKey)) {
            logger.warn { "Routing message to DLQ due to circuit open in ${messagePreStore.routing()}" }
            val dlqMessage = deadLetterQueueRepository.save(messagePreStore, channel.channelId!!, producer.producerId!!)

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

        logger.info { "Publishing message to ${messagePreStore.routing()}" }
        return messageRepository.save(messagePreStore, channel.channelId!!, producer.producerId!!)
    }
}
