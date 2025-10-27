package com.ph.syntropyengine.broker.api

import com.ph.syntropyengine.broker.model.Channel
import com.ph.syntropyengine.broker.model.ChannelType
import com.ph.syntropyengine.broker.service.ChannelService
import jakarta.validation.constraints.NotEmpty
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ChannelController(
    private val channelService: ChannelService
) {

    @PutMapping("/channel")
    fun createChannel(@RequestBody request: CreateChannelRequest): Channel {
        return channelService.createChannel(request.name, request.channelType, request.routingKeys)
    }
}

data class CreateChannelRequest(
    @param:NotEmpty
    val name: String,
    @param:NotEmpty
    val channelType: ChannelType,
    @param:NotEmpty
    val routingKeys: List<String>,
)
