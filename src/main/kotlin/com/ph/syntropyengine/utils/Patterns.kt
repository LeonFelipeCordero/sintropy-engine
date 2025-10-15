package com.ph.syntropyengine.utils

import java.util.UUID

object Patterns {

    fun loggingPair(channelId: UUID, routingKey: String): String {
        return "[$channelId|$routingKey]"
    }
}