package com.ph.sintropyengine.broker.service

import com.ph.sintropyengine.broker.model.Consumer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
class ConsumerService(
    private val channelService: ChannelService,
//    private val consumerRepository: ConsumerRepository,
//    private val connectionRouter: ConnectionRouter,
) {

    @Transactional
    suspend fun createConsumer(channelName: String, routingKey: String, connectionId: String): Consumer {
        val channel =
            channelService.findByName(channelName) ?: throw IllegalStateException("Channel $channelName not found")

        if (!channel.containsRoutingKey(routingKey)) {
            throw IllegalStateException("Routing key $routingKey not found in channel $channelName")
        }

        val consumer = Consumer(
            channelId = channel.channelId!!,
            routingKey = routingKey,
            connectionId = connectionId,
        )
//        val storedConsumer = consumerRepository.save(consumer)

//        connectionRouter.add(consumer)

        return consumer
    }

//    fun findById(consumerId: UUID): Consumer? = consumerRepository.findById(consumerId)

//    fun findByChannel(channelName: String): List<Consumer> =
//        channelService.findByIdName(channelName)?.let {
//            consumerRepository.findByChannel(channelName)
//        } ?: throw IllegalStateException("Channel $channelName not found")

//    @Transactional
//    fun deleteConsumer(consumerId: UUID) =
//        consumerRepository.findById(consumerId)?.let {
//            consumerRepository.delete(consumerId)
//        } ?: throw IllegalStateException("Consumer $consumerId not found")

//    fun deleteConsumerByConnection(connectionId: String) {
//
//    }
}