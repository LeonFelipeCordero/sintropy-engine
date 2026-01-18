package com.ph.sintropyengine.broker.channel.api.response

import com.ph.sintropyengine.broker.channel.model.ChannelLink
import java.util.UUID

data class ChannelLinkResponse(
    val channelLinkId: UUID,
    val sourceChannelName: String,
    val targetChannelName: String,
    val sourceRoutingKey: String,
    val targetRoutingKey: String,
    val enabled: Boolean,
)

fun ChannelLink.toResponse(
    sourceChannelName: String,
    targetChannelName: String,
): ChannelLinkResponse =
    ChannelLinkResponse(
        channelLinkId = channelLinkId!!,
        sourceChannelName = sourceChannelName,
        targetChannelName = targetChannelName,
        sourceRoutingKey = sourceRoutingKey,
        targetRoutingKey = targetRoutingKey,
        enabled = enabled,
    )
