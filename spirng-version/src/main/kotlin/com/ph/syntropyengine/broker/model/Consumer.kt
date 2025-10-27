package com.ph.syntropyengine.broker.model

import java.util.UUID

data class Consumer(
    val consumerId: UUID? = null,
    val channelId: UUID,
    val routingKey: String,
    val connectionType: ConnectionType// TODO this is no needed delete later
)
