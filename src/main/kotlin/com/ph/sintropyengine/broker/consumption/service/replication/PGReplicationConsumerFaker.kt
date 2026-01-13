package com.ph.sintropyengine.broker.consumption.service.replication

import com.ph.sintropyengine.broker.consumption.model.Message
import kotlinx.coroutines.channels.Channel

class PGReplicationConsumerFaker(
    private val channel: Channel<Message>,
) : PGReplicationConsumer {
    constructor() : this(Channel<Message>())

    override suspend fun startConsuming() {
    }

    override fun channel(): Channel<Message> = channel
}
