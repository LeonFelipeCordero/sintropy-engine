package com.ph.sintropyengine.broker.producer.api.response

import com.ph.sintropyengine.broker.producer.model.Producer

data class ProducerResponse(
    val name: String,
)

fun Producer.toResponse(): ProducerResponse = ProducerResponse(name = name)
