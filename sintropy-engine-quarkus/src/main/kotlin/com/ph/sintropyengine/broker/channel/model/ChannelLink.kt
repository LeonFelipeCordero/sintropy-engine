package com.ph.sintropyengine.broker.channel.model

import java.time.OffsetDateTime
import java.util.UUID

data class ChannelLink(
    val channelLinkId: UUID? = null,
    val sourceChannelId: UUID,
    val targetChannelId: UUID,
    val sourceRoutingKey: String,
    val targetRoutingKey: String,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null,
    val enabled: Boolean = true,
)
