# API Contracts

## Purpose

This document defines all REST API endpoints, request/response formats, and HTTP status codes for Sintropy Engine.

## Invariants

- All endpoints produce and consume `application/json`
- UUIDs are used for all entity identifiers
- Timestamps are ISO-8601 format with timezone (OffsetDateTime)
- JSONB fields (message, headers) are returned as raw JSON strings
- 404 returned when referenced entity not found
- 204 returned for successful operations with no response body
- 201 returned for successful creation with entity in response body

## Constraints

- Channel names: max 256 characters, must be unique
- Producer names: max 128 characters, must be unique
- Routing keys: max 128 characters (255 for channel_links)
- Pagination defaults: pageSize=100, page=0

---

## Channel Domain

### POST /channels
Creates a new channel.

**Request:**
```json
{
  "name": "string",
  "channelType": "QUEUE" | "STREAM",
  "routingKeys": ["string"],
  "consumptionType": "STANDARD" | "FIFO" | null
}
```

**Response (201):**
```json
{
  "name": "string",
  "channelType": "QUEUE" | "STREAM",
  "routingKeys": ["string"],
  "consumptionType": "STANDARD" | "FIFO" | null,
  "routingKeysCircuitState": [
    {
      "routingKey": "string",
      "circuitState": "CLOSED" | "OPEN"
    }
  ]
}
```

**Constraints:**
- `consumptionType` required only when `channelType` is `QUEUE`
- `consumptionType` must be null when `channelType` is `STREAM`

---

### GET /channels/{name}
Returns channel by name.

**Response (200):**
```json
{
  "name": "string",
  "channelType": "QUEUE" | "STREAM",
  "routingKeys": ["string"],
  "consumptionType": "STANDARD" | "FIFO" | null,
  "routingKeysCircuitState": [
    {
      "routingKey": "string",
      "circuitState": "CLOSED" | "OPEN"
    }
  ]
}
```

**Response (404):** Channel not found

---

### DELETE /channels/{name}
Deletes a channel and all associated data.

**Response (204):** Success, no content
**Response (404):** Channel not found

---

### POST /channels/{name}/routing-keys
Adds a routing key to an existing channel.

**Request:**
```json
{
  "routingKey": "string"
}
```

**Response (204):** Success, no content

---

## Channel Links

### POST /channels/links
Creates a link between two channels for message routing.

**Request:**
```json
{
  "sourceChannelName": "string",
  "targetChannelName": "string",
  "sourceRoutingKey": "string",
  "targetRoutingKey": "string"
}
```

**Response (201):**
```json
{
  "channelLinkId": "uuid",
  "sourceChannelName": "string",
  "targetChannelName": "string",
  "sourceRoutingKey": "string",
  "targetRoutingKey": "string",
  "enabled": true
}
```

**Constraints:**
- Cannot link STANDARD queue to STREAM or FIFO queue (ordering guarantee violation)
- Both channels and routing keys must exist

---

### GET /channels/links/{linkId}
Returns a channel link by ID.

**Response (200):**
```json
{
  "channelLinkId": "uuid",
  "sourceChannelName": "string",
  "targetChannelName": "string",
  "sourceRoutingKey": "string",
  "targetRoutingKey": "string",
  "enabled": boolean
}
```

**Response (404):** Link not found

---

### GET /channels/{channelName}/links/outgoing
Returns all outgoing links from a channel.

**Response (200):** Array of ChannelLinkResponse

---

### GET /channels/{channelName}/links/incoming
Returns all incoming links to a channel.

**Response (200):** Array of ChannelLinkResponse

---

### DELETE /channels/links/{linkId}
Deletes a channel link.

**Response (204):** Success, no content

---

### PUT /channels/links/{linkId}/enable
Enables a disabled channel link.

**Response (204):** Success, no content

---

### PUT /channels/links/{linkId}/disable
Disables a channel link (stops message routing).

**Response (204):** Success, no content

---

## Producer Domain

### POST /producers
Creates a new producer linked to a channel.

**Request:**
```json
{
  "name": "string",
  "channelName": "string"
}
```

**Response (201):**
```json
{
  "name": "string",
  "channelName": "string"
}
```

---

### GET /producers/{name}
Returns producer by name.

**Response (200):**
```json
{
  "name": "string",
  "channelName": "string"
}
```

**Response (404):** Producer not found

---

### GET /producers/channel/{channelName}
Returns all producers for a channel.

**Response (200):** Array of ProducerResponse
**Response (404):** Channel not found

---

### DELETE /producers/{name}
Deletes a producer.

**Response (204):** Success, no content
**Response (404):** Producer not found

---

### POST /producers/messages
Publishes a message to a channel.

**Request:**
```json
{
  "channelName": "string",
  "producerName": "string",
  "routingKey": "string",
  "message": "string (JSON)",
  "headers": "string (JSON)"
}
```

**Response (201):**
```json
{
  "messageId": "uuid",
  "timestamp": "2024-01-15T10:30:00Z",
  "channelName": "string",
  "producerName": "string",
  "routingKey": "string",
  "message": "string (JSON)",
  "headers": "string (JSON)",
  "status": "READY" | "FAILED",
  "lastDelivered": null,
  "deliveredTimes": 0,
  "originMessageId": "uuid" | null
}
```

**Constraints:**
- Producer must be linked to the specified channel
- If circuit is OPEN (FIFO channels), message goes directly to DLQ with status FAILED

---

## Consumption Domain

### POST /queues/poll
Polls messages from a queue.

**Request:**
```json
{
  "channelName": "string",
  "routingKey": "string",
  "pollingCount": 10
}
```

**Response (200):**
```json
[
  {
    "messageId": "uuid",
    "timestamp": "2024-01-15T10:30:00Z",
    "channelName": "string",
    "producerName": "string",
    "routingKey": "string",
    "message": "string (JSON)",
    "headers": "string (JSON)",
    "status": "IN_FLIGHT",
    "lastDelivered": "2024-01-15T10:30:00Z",
    "deliveredTimes": 1,
    "originMessageId": "uuid" | null
  }
]
```

**Behavior:**
- STANDARD: Returns up to `pollingCount` READY messages, or IN_FLIGHT messages older than 15 minutes with < 4 deliveries
- FIFO: Returns READY messages only if no IN_FLIGHT messages exist for the routing key
- Messages transition from READY to IN_FLIGHT

---

### POST /queues/messages/{messageId}/failed
Marks a message as failed.

**Response (204):** Success, no content

**Side Effects:**
- Message copied to dead_letter_queue
- Message deleted from messages table
- (FIFO) Circuit breaker opens, all remaining messages moved to DLQ

---

### DELETE /queues/messages/{messageId}
Dequeues (acknowledges) a successfully processed message.

**Response (204):** Success, no content

**Side Effects:**
- Message deleted from messages table
- message_log.processed set to true

---

## Dead Letter Queue

### GET /dead-letter-queue/channels/{channelName}/routing-keys/{routingKey}
Lists messages in the DLQ for a channel/routing key.

**Query Parameters:**
- `pageSize` (optional, default 100)
- `page` (optional, default 0)

**Response (200):**
```json
[
  {
    "messageId": "uuid",
    "timestamp": "2024-01-15T10:30:00Z",
    "channelName": "string",
    "producerName": "string",
    "routingKey": "string",
    "message": "string (JSON)",
    "headers": "string (JSON)",
    "originMessageId": "uuid" | null,
    "deliveredTimes": 4,
    "failedAt": "2024-01-15T11:00:00Z"
  }
]
```

---

### POST /dead-letter-queue/messages/{messageId}/recover
Recovers a single message from DLQ back to the main queue.

**Response (200):** MessageResponse (recovered message)

---

### POST /dead-letter-queue/messages/recover
Recovers multiple messages from DLQ.

**Request:**
```json
{
  "messageIds": ["uuid", "uuid"]
}
```

**Response (200):** Array of MessageResponse

---

### POST /dead-letter-queue/channels/{channelName}/routing-keys/{routingKey}/recover
Recovers all messages in DLQ for a channel/routing key.

**Response (200):**
```json
{
  "recoveredCount": 42
}
```

---

## Circuit Breaker

### GET /circuit-breakers/open
Lists all open circuit breakers.

**Response (200):**
```json
[
  {
    "channelName": "string",
    "routingKey": "string",
    "state": "OPEN",
    "openedAt": "2024-01-15T10:30:00Z",
    "failedMessageId": "uuid"
  }
]
```

---

### GET /circuit-breakers/channels/{channelName}
Lists all circuit breakers for a channel.

**Response (200):** Array of CircuitBreakerResponse

---

### GET /circuit-breakers/channels/{channelName}/routing-keys/{routingKey}
Gets a specific circuit breaker.

**Response (200):** CircuitBreakerResponse
**Response (204):** Circuit breaker not found (no content)

---

### GET /circuit-breakers/channels/{channelName}/routing-keys/{routingKey}/state
Gets just the circuit state.

**Response (200):**
```json
{
  "state": "CLOSED" | "OPEN",
  "circuitBreaker": null
}
```

---

### POST /circuit-breakers/channels/{channelName}/routing-keys/{routingKey}/close
Closes an open circuit breaker.

**Query Parameters:**
- `recover` (optional, boolean): If true, also recovers all DLQ messages

**Response (200):**
```json
{
  "success": true,
  "recoveredCount": 0
}
```

---

## WebSocket Streaming

### WS /ws/streaming/{channelName}/{routingKey}
Real-time message streaming via WebSocket.

**Connection:** Establishes WebSocket connection for STREAM channels
**Messages:** Receives messages in real-time via PostgreSQL logical replication

---

## HTTP Status Codes Summary

| Code | Meaning |
|------|---------|
| 200 | Success with response body |
| 201 | Created with response body |
| 204 | Success with no content |
| 404 | Resource not found |
| 500 | Internal server error (check logs) |
