package com.ph.syntropyengine.broker.model

import java.util.UUID

data class Producer(
    val producerId: UUID? = null,
    val name: String,
    val channelId: UUID,
)
