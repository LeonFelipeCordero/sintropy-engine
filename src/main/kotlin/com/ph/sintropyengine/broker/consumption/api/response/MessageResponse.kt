package com.ph.sintropyengine.broker.consumption.api.response

import com.ph.sintropyengine.broker.consumption.model.Message
import com.ph.sintropyengine.broker.consumption.model.MessageStatus
import java.time.OffsetDateTime
import java.util.UUID

data class MessageResponse(
    val messageId: UUID,
    val timestamp: OffsetDateTime?,
    val channelName: String,
    val producerName: String,
    val routingKey: String,
    val message: String,
    val headers: String,
    val status: MessageStatus,
    val lastDelivered: OffsetDateTime?,
    val deliveredTimes: Int,
    val originMessageId: UUID?,
)

fun Message.toResponse(
    channelName: String,
    producerName: String,
): MessageResponse =
    MessageResponse(
        messageId = messageId,
        timestamp = timestamp,
        channelName = channelName,
        producerName = producerName,
        routingKey = routingKey,
        message = message.data(),
        headers = headers.data(),
        status = status,
        lastDelivered = lastDelivered,
        deliveredTimes = deliveredTimes,
        originMessageId = originMessageId,
    )
