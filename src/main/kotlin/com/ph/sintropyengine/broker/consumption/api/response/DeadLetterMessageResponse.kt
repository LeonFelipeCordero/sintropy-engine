package com.ph.sintropyengine.broker.consumption.api.response

import com.ph.sintropyengine.broker.consumption.model.DeadLetterMessage
import java.time.OffsetDateTime
import java.util.UUID

data class DeadLetterMessageResponse(
    val messageId: UUID,
    val timestamp: OffsetDateTime,
    val channelName: String,
    val producerName: String,
    val routingKey: String,
    val message: String,
    val headers: String,
    val originMessageId: UUID?,
    val deliveredTimes: Int,
    val failedAt: OffsetDateTime,
)

fun DeadLetterMessage.toResponse(
    channelName: String,
    producerName: String,
): DeadLetterMessageResponse =
    DeadLetterMessageResponse(
        messageId = messageUuid!!,
        timestamp = timestamp,
        channelName = channelName,
        producerName = producerName,
        routingKey = routingKey,
        message = message.data(),
        headers = headers.data(),
        originMessageId = originMessageId,
        deliveredTimes = deliveredTimes,
        failedAt = failedAt,
    )
