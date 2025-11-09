package com.ph.sintropyengine.broker.replication

import com.ph.sintropyengine.broker.model.Message
import kotlinx.coroutines.channels.Channel

interface PGReplicationConsumer {
    suspend fun startConsuming()
    fun channel(): Channel<Message>
}
