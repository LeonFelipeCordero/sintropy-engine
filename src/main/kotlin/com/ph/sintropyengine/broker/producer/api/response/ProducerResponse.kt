package com.ph.sintropyengine.broker.producer.api.response

import com.ph.sintropyengine.broker.producer.model.Producer

data class ProducerResponse(
    val name: String,
    val channelName: String,
)

fun Producer.toResponse(channelName: String): ProducerResponse =
    ProducerResponse(
        name = name,
        channelName = channelName,
    )
