package com.ph.syntropyengine.broker.service

import com.ph.syntropyengine.broker.model.ConnectionType
import com.ph.syntropyengine.broker.model.Consumer
import com.ph.syntropyengine.broker.repository.ConsumerRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ConsumerService(
    private val channelService: ChannelService,
    private val consumerRepository: ConsumerRepository,
    private val messageRouter: MessageRouter,
    private val pollingQueue: PollingStandardQueue
) {

    @Transactional
    suspend fun createConsumer(channelName: String, routingKey: String, connectionType: ConnectionType): Consumer {
        val channel =
            channelService.findByIdName(channelName) ?: throw IllegalStateException("Channel $channelName not found")

        if (!channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("Routing key $routingKey not found in channel $channelName")
        }

        val consumer = Consumer(
            channelId = channel.channelId!!,
            routingKey = routingKey,
            connectionType = connectionType
        )
        val storedConsumer = consumerRepository.save(consumer)

        // TODO: For now it uses this in memory approach, things to consider are:
        // This has to be transactional, and how to make sure the consumer get's available in the channel,
        // after being save?
        // When a consumer is explicitly created, it will hold a live connection and receive updates via WS (at start)
        // Consumers can poll directly from a channel with poll mechanisim
        messageRouter.addConsumer(storedConsumer)
//        pollingQueue.

        return storedConsumer
    }

    fun findById(consumerId: UUID): Consumer? = consumerRepository.findById(consumerId)

    fun findByChannel(channelName: String): List<Consumer> =
        channelService.findByIdName(channelName)?.let {
            consumerRepository.findByChannel(channelName)
        } ?: throw IllegalStateException("Channel $channelName not found")

    @Transactional
    fun deleteConsumer(consumerId: UUID) =
        consumerRepository.findById(consumerId)?.let {
            consumerRepository.delete(consumerId)
        } ?: throw IllegalStateException("Consumer $consumerId not found")
}