package com.ph.syntropyengine.broker.replication

import com.ph.syntropyengine.broker.model.Message
import kotlinx.coroutines.channels.Channel

interface PGReplicationConsumer {
    suspend fun startConsuming()
    fun channel(): Channel<Message>
}
