package com.ph.sintropyengine.broker.model

import java.util.UUID

data class Consumer(
    val consumerId: UUID? = null,
    val channelId: UUID,
    val routingKey: String,
    val connectionId: String,
)
