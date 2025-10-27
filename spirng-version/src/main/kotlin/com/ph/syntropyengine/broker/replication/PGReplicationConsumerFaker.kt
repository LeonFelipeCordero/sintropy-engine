package com.ph.syntropyengine.broker.replication

import com.ph.syntropyengine.broker.model.Message
import kotlinx.coroutines.channels.Channel

class PGReplicationConsumerFaker(
    private val channel: Channel<Message>,
) : PGReplicationConsumer {

    constructor() : this(Channel<Message>())

    override suspend fun startConsuming() {
    }

    override fun channel(): Channel<Message> {
        return channel
    }
}