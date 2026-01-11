package com.ph.sintropyengine.broker.consumption.service.replication

import com.ph.sintropyengine.broker.consumption.model.Message
import kotlinx.coroutines.channels.Channel

interface PGReplicationConsumer {
    suspend fun startConsuming()
    fun channel(): Channel<Message>
}
