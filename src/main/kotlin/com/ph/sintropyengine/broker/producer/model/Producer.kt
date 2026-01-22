package com.ph.sintropyengine.broker.producer.model

import java.util.UUID

data class Producer(
    val producerId: Long? = null,
    val producerUuid: UUID? = null,
    val name: String,
    val channelId: Long,
)
