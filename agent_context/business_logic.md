# Business Logic

## Purpose

This document defines the core business rules, domain workflows, validation logic, and invariants for Sintropy Engine.

## Invariants

- A producer can publish to any channel (producers are not linked to specific channels)
- A message must have a valid routing key that exists on its channel
- Circuit breakers only apply to FIFO channels and streams (not STANDARD queues)
- Messages with `origin_message_id` set are routed copies and do not trigger further routing
- Once a message enters the DLQ, it retains its original `message_id` for traceability

## Guarantees

- Every published message is logged to `message_log` (event sourcing)
- Failed messages are never lost - they are preserved in the dead letter queue
- Circuit breaker state is always consistent with routing key lifecycle
- Channel links respect ordering guarantees (STANDARD cannot link to FIFO/Stream)

## Constraints

- Producer must exist before publishing messages
- Channel and routing key must exist before publishing to that channel/key combination
- Circuit must be CLOSED to publish to a FIFO channel (otherwise goes to DLQ)

---

## Channel Domain

### Channel Types

| Type | Purpose | Circuit Breaker | Message Ordering |
|------|---------|-----------------|------------------|
| QUEUE + STANDARD | High-throughput parallel processing | No | Not guaranteed |
| QUEUE + FIFO | Ordered processing, one at a time | Yes | Guaranteed |
| STREAM | Real-time WebSocket delivery | Yes | Guaranteed |

### Channel Creation Rules
- `channelType` is required (QUEUE or STREAM)
- `consumptionType` is required for QUEUE channels (STANDARD or FIFO)
- `consumptionType` must be null for STREAM channels
- At least one routing key should be provided
- Channel names must be unique across the system

### Routing Key Rules
- Routing keys are scoped to a single channel
- Adding a routing key auto-creates a circuit breaker (via trigger)
- Deleting a routing key auto-deletes its circuit breaker (via trigger)

---

## Channel Links

### Link Validation Rules

```
Source Channel Type    →    Target Channel Type    =    Allowed?
─────────────────────────────────────────────────────────────────
QUEUE + STANDARD      →    QUEUE + STANDARD       =    YES
QUEUE + STANDARD      →    QUEUE + FIFO           =    NO (ordering violation)
QUEUE + STANDARD      →    STREAM                 =    NO (ordering violation)
QUEUE + FIFO          →    QUEUE + STANDARD       =    YES
QUEUE + FIFO          →    QUEUE + FIFO           =    YES
QUEUE + FIFO          →    STREAM                 =    YES
STREAM                →    Any                    =    YES
```

**Rationale:** STANDARD queues process messages in parallel without ordering guarantees. Linking them to FIFO/Stream targets would violate the ordering guarantees those targets expect.

### Link Behavior
- Messages are copied to target channel via database trigger
- Routed messages have `origin_message_id` set to the original message's ID
- Messages with `origin_message_id` do not trigger further routing (prevents infinite loops)
- Links can be enabled/disabled without deletion
- Disabled links do not route messages

---

## Producer Domain

### Producer Rules
- Producer name must be unique across the system
- Producers are independent entities not linked to any specific channel
- A producer can publish messages to any channel
- Deleting a producer does not delete its messages

### Message Publishing Workflow

```
1. Validate producer exists
2. Validate channel exists
3. Validate routing key exists on channel
4. Check circuit breaker state (FIFO/Stream only)
   └─ If OPEN: Insert directly to DLQ, return with status=FAILED
   └─ If CLOSED: Continue to step 5
5. Insert message with status=READY
6. Trigger: Copy to message_log
7. Trigger: Copy to linked channels (if any)
8. Return message with status=READY
```

---

## Consumption Domain

### Message Status Lifecycle

```
                    ┌─────────────────────────────────────────┐
                    │                                         │
                    ▼                                         │
┌───────────┐   Poll   ┌─────────────┐   Timeout (15min)     │
│   READY   │─────────▶│  IN_FLIGHT  │───────────────────────┘
└───────────┘          └─────────────┘        (STANDARD only, max 4 times)
                             │
           ┌─────────────────┼─────────────────┐
           │                 │                 │
           ▼                 ▼                 │
      DELETE (ACK)    markAsFailed()          │
           │                 │                 │
           ▼                 ▼                 │
     [removed]         ┌─────────┐            │
                       │ FAILED  │            │
                       └─────────┘            │
                             │                │
                             ▼                │
                      [Trigger: copy to DLQ]  │
                      [Trigger: delete msg]   │
                      [FIFO: open circuit]    │
                      [FIFO: move all to DLQ]─┘
```

### STANDARD Queue Consumption
- Multiple messages can be IN_FLIGHT simultaneously
- Messages retry after 15-minute timeout if still IN_FLIGHT
- Maximum 4 delivery attempts before message is abandoned
- Abandoned messages remain IN_FLIGHT (no auto-failure)
- No circuit breaker - failures don't affect other messages

### FIFO Queue Consumption
- Only one message can be IN_FLIGHT at a time per routing key
- If any message is IN_FLIGHT, poll returns empty
- First failure opens circuit breaker for that routing key
- Circuit open: all remaining READY messages move to DLQ
- Circuit open: new messages go directly to DLQ
- Must close circuit and recover messages to resume processing

### Stream Consumption
- Messages delivered via WebSocket in real-time
- Uses PostgreSQL logical replication (wal2json)
- Circuit breaker behavior same as FIFO

---

## Circuit Breaker

### States

| State | Meaning | Behavior |
|-------|---------|----------|
| CLOSED | Normal operation | Messages flow normally |
| OPEN | Failure detected | New messages go to DLQ, remaining messages in DLQ |

### State Transitions

```
CLOSED ──[message fails (FIFO/Stream)]──▶ OPEN
  ▲                                         │
  │                                         │
  └────[closeCircuit() called]──────────────┘
```

### Circuit Breaker Fields
- `state`: CLOSED or OPEN
- `opened_at`: Timestamp when circuit opened
- `failed_message_id`: ID of the message that caused the circuit to open

### Recovery Options
1. `closeCircuit()` - Just close the circuit, leave DLQ messages as-is
2. `closeCircuitAndRecover()` - Close circuit AND move all DLQ messages back to main queue

---

## Dead Letter Queue

### Entry Criteria
1. Message explicitly marked as FAILED via API
2. Message published to FIFO channel with OPEN circuit
3. Message was READY/IN_FLIGHT when circuit opened (bulk move)

### DLQ Message Properties
- Preserves original `message_id` for traceability
- Preserves `origin_message_id` if message was routed
- Records `failed_at` timestamp
- Records `delivered_times` at time of failure

### Recovery Options
1. Recover single message by ID
2. Recover multiple messages by ID list
3. Recover all messages for a channel/routing key combination

### Recovery Behavior
- Message is re-inserted to `messages` table with status=READY
- `delivered_times` reset to 0
- `last_delivered` set to null
- Original `message_id` and `timestamp` preserved
- DLQ entry deleted after successful recovery

---

## IaC (Infrastructure as Code)

### File Location
`$HOME/.sintropy-engine/init.json`

### Processing Rules
- File is processed at application startup
- SHA-256 hash is calculated and compared to stored hash
- If hash unchanged, initialization is skipped
- If hash changed, reconciliation is performed

### Reconciliation Logic

```
1. Load current state (channels, producers, links)
2. Compare with desired state from init.json
3. Delete resources NOT in desired state:
   - Links first (to avoid FK violations)
   - Producers second
   - Channels last
4. Create resources IN desired state but NOT in current:
   - Channels first
   - Producers second
   - Links last
5. Update stored hash
```

### IaC File Format
```json
{
  "channels": [
    {
      "name": "channel-name",
      "channelType": "QUEUE",
      "routingKeys": ["key1", "key2"],
      "consumptionType": "STANDARD"
    }
  ],
  "producers": [
    {
      "name": "producer-name"
    }
  ],
  "channelLinks": [
    {
      "sourceChannelName": "source",
      "targetChannelName": "target",
      "sourceRoutingKey": "key1",
      "targetRoutingKey": "key2"
    }
  ]
}
```

---

## Validation Summary

| Field | Constraints |
|-------|-------------|
| channel.name | Required, max 256 chars, unique |
| channel.channelType | Required, enum: QUEUE, STREAM |
| channel.consumptionType | Required for QUEUE, enum: STANDARD, FIFO |
| routing_key | Max 128 chars, unique per channel |
| producer.name | Required, max 128 chars, unique |
| message.message | Required, valid JSON string |
| message.headers | Required, valid JSON string |
| pollingCount | Positive integer |
| pageSize | Positive integer, default 100 |
| page | Non-negative integer, default 0 |
