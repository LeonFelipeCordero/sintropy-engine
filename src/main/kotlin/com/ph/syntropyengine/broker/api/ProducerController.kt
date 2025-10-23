package com.ph.syntropyengine.broker.api

import com.ph.syntropyengine.broker.model.Message
import com.ph.syntropyengine.broker.model.Producer
import com.ph.syntropyengine.broker.service.ProducerService
import jakarta.validation.constraints.NotEmpty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

@RestController
class ProducerController(
    private val producerService: ProducerService,
) {

    @PutMapping("/producer")
    fun createProducer(@RequestBody request: CreateProuderRequest): Producer {
        return producerService.createProducer(request.name, request.channelName)
    }

    @GetMapping(
        path = ["/producer/{id}"],
    )
    fun getById(@PathVariable id: UUID): Producer? {
        return producerService.findById(id)
    }

    @PutMapping("/producer/{id}/message")
    fun createMessage(@RequestBody request: CreateMessageRequest, @PathVariable id: String): Message {
        return producerService.publishMessage(request.toDto())
    }
}

data class CreateProuderRequest(
    @param:NotEmpty
    val name: String,
    @param:NotEmpty
    val channelName: String,
)

data class CreateMessageRequest(
    val messageId: UUID,
    val timestamp: OffsetDateTime,
    val channelId: UUID,
    val producerId: UUID,
    @param:NotEmpty
    val routingKey: String,
    @param:NotEmpty
    val message: String,
    @param:NotEmpty
    val headers: String,
) {
    fun toDto(): Message {
        return Message(
            messageId = messageId,
            timestamp = timestamp,
            channelId = channelId,
            producerId = producerId,
            routingKey = routingKey,
            message = message,
            headers = headers,
        )
    }
}