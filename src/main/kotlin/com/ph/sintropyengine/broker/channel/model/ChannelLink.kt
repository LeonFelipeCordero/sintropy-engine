package com.ph.sintropyengine.broker.channel.model

import java.time.OffsetDateTime
import java.util.UUID

data class ChannelLink(
    val channelLinkId: Long? = null,
    val channelLinkUuid: UUID? = null,
    val sourceChannelId: Long,
    val targetChannelId: Long,
    val sourceRoutingKey: String,
    val targetRoutingKey: String,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null,
    val enabled: Boolean = true,
)
