package com.ph.sintropyengine.broker.consumption.api.response

import com.ph.sintropyengine.broker.consumption.model.MessageLog
import java.time.OffsetDateTime
import java.util.UUID

data class MessageLogResponse(
    val messageId: UUID,
    val timestamp: OffsetDateTime,
    val channelName: String,
    val producerName: String,
    val routingKey: String,
    val message: String,
    val headers: String,
    val processed: Boolean,
    val originMessageId: UUID?,
)

fun MessageLog.toResponse(
    channelName: String,
    producerName: String,
): MessageLogResponse =
    MessageLogResponse(
        messageId = messageUuid,
        timestamp = timestamp,
        channelName = channelName,
        producerName = producerName,
        routingKey = routingKey,
        message = message.data(),
        headers = headers.data(),
        processed = processed,
        originMessageId = originMessageId,
    )
