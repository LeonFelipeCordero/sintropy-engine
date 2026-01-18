# Architecture

## Purpose

This document governs the system architecture, component structure, and design patterns of Sintropy Engine - a message broker built with Quarkus and Kotlin.

## Invariants

- Messages are immutable once created
- Every message insert triggers automatic copy to `message_log` table
- Advisory locks (`pg_try_advisory_xact_lock`) must be used for all message polling operations
- Channel links can only route from FIFO/Stream sources to any target; STANDARD sources cannot link to FIFO/Stream targets
- Circuit breakers are auto-created for every routing key via database trigger
- FIFO channels block polling when any message is IN_FLIGHT
- STANDARD channels allow concurrent message processing with retry logic

## Guarantees

- Exactly-once delivery semantics via PostgreSQL advisory locks
- Message ordering preserved for FIFO queues and streams
- Failed messages are always preserved in the dead letter queue
- Event sourcing via `message_log` provides complete audit trail
- Circuit breaker automatically opens on first failure in FIFO channels, routing all remaining messages to DLQ

## Constraints

- PostgreSQL 17+ required with `wal2json` plugin for logical replication
- Maximum 4 delivery attempts for STANDARD queue messages
- 15-minute timeout between delivery attempts for IN_FLIGHT messages
- Channel names must be unique (enforced by unique index)
- Producer names must be unique (enforced by unique index)
- Routing keys are scoped to channels (composite primary key: routing_key + channel_id)

## Non-Goals

- Multi-region replication
- Message encryption at rest
- Rate limiting per producer
- Message TTL/expiration
- Consumer groups or partitioning

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Sintropy Engine                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   ┌──────────────┐    ┌──────────────┐    ┌──────────────────────┐  │
│   │   Channel    │    │   Producer   │    │    Consumption       │  │
│   │   Domain     │    │   Domain     │    │    Domain            │  │
│   ├──────────────┤    ├──────────────┤    ├──────────────────────┤  │
│   │ - Channels   │    │ - Producers  │    │ - Message Polling    │  │
│   │ - Routing    │    │ - Message    │    │ - Circuit Breakers   │  │
│   │   Keys       │    │   Publishing │    │ - Dead Letter Queue  │  │
│   │ - Channel    │    │              │    │ - WebSocket Streaming│  │
│   │   Links      │    │              │    │ - Message Recovery   │  │
│   └──────────────┘    └──────────────┘    └──────────────────────┘  │
│                                                                      │
│   ┌──────────────┐                                                   │
│   │     IaC      │                                                   │
│   │   Domain     │                                                   │
│   ├──────────────┤                                                   │
│   │ - Declarative│                                                   │
│   │   Setup via  │                                                   │
│   │   init.json  │                                                   │
│   └──────────────┘                                                   │
│                                                                      │
├─────────────────────────────────────────────────────────────────────┤
│                         PostgreSQL 17+                               │
│   ┌────────────────────────────────────────────────────────────┐    │
│   │  Tables  │  Triggers  │  Publications  │  Advisory Locks   │    │
│   └────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

## Channel Types

| Type | Consumption | Concurrency | Circuit Breaker | Retry Behavior |
|------|-------------|-------------|-----------------|----------------|
| QUEUE + STANDARD | Parallel processing | Multiple consumers | No | 4 attempts, 15-min timeout |
| QUEUE + FIFO | Ordered processing | Single message at a time | Yes | First failure opens circuit |
| STREAM | Real-time WebSocket | Via logical replication | Yes | First failure opens circuit |

## Message Flow

```
Producer.publishMessage()
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  Check Circuit Breaker State                                 │
│  ┌─────────────────┐     ┌──────────────────────────────┐   │
│  │ Circuit CLOSED  │────▶│ Insert into messages table    │   │
│  └─────────────────┘     └──────────────────────────────┘   │
│  ┌─────────────────┐     ┌──────────────────────────────┐   │
│  │ Circuit OPEN    │────▶│ Insert directly to DLQ       │   │
│  └─────────────────┘     └──────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
         │ (if circuit closed)
         ▼
┌─────────────────────────────────────────────────────────────┐
│  Trigger: insert_into_message_log                            │
│  - Copies message to message_log table                       │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  Trigger: route_message_after_insert                         │
│  - Copies message to linked target channels (if any)         │
│  - Only for messages without origin_message_id               │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  Consumer polls via POST /queues/poll                        │
│  - Advisory lock acquired: pg_try_advisory_xact_lock(hash)   │
│  - Message status: READY → IN_FLIGHT                         │
│  - delivered_times incremented                               │
└─────────────────────────────────────────────────────────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
 SUCCESS    FAILURE
    │         │
    ▼         ▼
DELETE   POST /messages/{id}/failed
    │         │
    ▼         ▼
Trigger:  Trigger: auto_delete_on_failed
mark_as_  - Copies to dead_letter_queue
delivered - Deletes from messages
_in_msg_  - Deletes from message_log
log       - (FIFO) Trigger: open_circuit_on_failed_delete
              - Opens circuit breaker
              - Moves ALL remaining messages to DLQ
```

## Layer Architecture

Each domain follows a consistent 4-layer pattern:

```
broker/{domain}/
├── api/                    # REST endpoints (JAX-RS @Path resources)
│   └── response/           # Response DTOs with toResponse() extensions
├── service/                # Business logic (@ApplicationScoped)
├── repository/             # Database access (JOOQ DSLContext)
└── model/                  # Domain objects (data classes)
```

## Key Technical Patterns

### Advisory Lock for Message Polling
```sql
-- Hash function: (channelId.toString() + routingKey).hashCode()
pg_try_advisory_xact_lock(:hash)
```
- Lock is transaction-scoped (released on commit/rollback)
- Prevents duplicate message delivery across consumers

### FIFO Polling Guard
```sql
-- FIFO channels only poll READY messages when no IN_FLIGHT exists
AND NOT EXISTS (
    SELECT 1 FROM messages
    WHERE channel_id = :channelId
    AND routing_key = :routingKey
    AND status = 'IN_FLIGHT'
)
```

### STANDARD Retry Logic
```sql
-- STANDARD channels retry IN_FLIGHT messages after 15 minutes, up to 4 times
AND (status = 'READY' OR
    (status = 'IN_FLIGHT' AND
     last_delivered < now() - interval '15 minutes' AND
     delivered_times < 4))
```

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Quarkus | 3.x | Application framework |
| Kotlin | 2.2.20 | Primary language |
| JOOQ | 3.19.26 | Type-safe SQL, code generation |
| PostgreSQL | 17+ | Database with logical replication |
| wal2json | - | PostgreSQL plugin for WAL streaming |
| Flyway | - | Database migrations |
| TestContainers | - | Integration testing |
| Java | 21 | Runtime target |

## Rationale

- **JOOQ over JPA**: Provides type-safe SQL while allowing complex queries with CTEs, advisory locks, and raw SQL when needed
- **PostgreSQL Triggers**: Ensures data consistency at database level without application coordination
- **Logical Replication**: Enables real-time WebSocket streaming without polling overhead
- **Advisory Locks**: Provides atomic message claiming without table-level locks or optimistic locking conflicts
- **Kotlin**: Concise syntax with null safety, extension functions for DTOs
