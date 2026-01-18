# Decisions Log

## Purpose

Chronological log of architectural and technical decisions for Sintropy Engine. Records context, decision, alternatives considered, and consequences.

## Invariants

- All major architectural decisions must be documented here
- Decisions are immutable once recorded (add new entries to supersede)
- Include alternatives considered for significant decisions

---

## 2024-01: Use PostgreSQL Advisory Locks for Message Polling

**Context**: Need to ensure exactly-once delivery of messages to consumers. Multiple consumers may poll the same queue simultaneously.

**Alternatives Considered**:
1. Optimistic locking with version column
2. SELECT FOR UPDATE with explicit row locking
3. Application-level distributed locks (Redis)
4. PostgreSQL advisory locks

**Decision**: Use `pg_try_advisory_xact_lock()` with transaction-scoped locks.

**Rationale**:
- Advisory locks are lightweight and don't block other queries
- Transaction-scoped means automatic release on commit/rollback
- No deadlock risk unlike row-level locks
- No external dependencies unlike Redis

**Consequences**:
- Hash function needed to convert (channelId, routingKey) to lock ID
- Very small collision probability (acceptable trade-off)
- Consumers must handle empty poll results gracefully

---

## 2024-01: Use Database Triggers for Event Sourcing

**Context**: Need to maintain audit trail of all messages and ensure data consistency across tables.

**Alternatives Considered**:
1. Application-level event publishing
2. Change Data Capture (CDC) tools
3. Database triggers

**Decision**: Use PostgreSQL triggers for all critical operations.

**Rationale**:
- Triggers execute within the same transaction (atomic)
- No risk of application forgetting to publish events
- Works even if application crashes mid-operation
- Simpler than CDC infrastructure

**Consequences**:
- 7 triggers created across the system
- Logic split between application and database
- Trigger debugging requires database access
- Schema changes may require trigger updates

---

## 2024-01: Use JOOQ Instead of JPA/Hibernate

**Context**: Need database access layer for complex queries including CTEs and raw SQL.

**Alternatives Considered**:
1. JPA/Hibernate with native queries
2. Spring Data JDBC
3. JOOQ with code generation
4. Raw JDBC with query builders

**Decision**: Use JOOQ with code generation from database schema.

**Rationale**:
- Type-safe SQL queries catch errors at compile time
- Full SQL power including CTEs, window functions, advisory locks
- Generated classes stay in sync with schema
- Better performance than JPA for complex queries

**Consequences**:
- Requires running database for code generation
- Generated files in separate source set (kotlin-gen)
- Team must learn JOOQ API
- No lazy loading (explicit queries needed)

---

## 2024-01: Implement Circuit Breaker at Database Level

**Context**: FIFO channels need circuit breaker to stop processing when a message fails, maintaining order guarantees.

**Alternatives Considered**:
1. Application-level circuit breaker (Resilience4j)
2. Database-level circuit breaker with triggers
3. Message status flags only

**Decision**: Implement circuit breaker using database triggers and dedicated table.

**Rationale**:
- State is shared across all application instances
- Atomic state changes within transactions
- Auto-created when routing key is added
- Survives application restarts

**Consequences**:
- `channel_circuit_breakers` table added
- Complex trigger logic for circuit opening
- Bulk message move to DLQ on circuit open
- Manual recovery required to close circuit

---

## 2024-02: Support Channel Linking with Message Routing

**Context**: Users need to route messages from one channel to another for fan-out patterns.

**Alternatives Considered**:
1. Application-level message copying
2. Database trigger-based routing
3. Message aliasing (virtual copies)

**Decision**: Use database trigger to copy messages to linked channels.

**Rationale**:
- Atomic with original message insert
- No additional API calls required
- Works regardless of producer implementation
- `origin_message_id` prevents routing loops

**Consequences**:
- `channel_links` table added
- `route_message_after_insert` trigger
- Validation required: STANDARD cannot link to FIFO/Stream
- Message duplication (storage cost)

---

## 2024-02: Use Logical Replication for WebSocket Streaming

**Context**: STREAM channels need real-time message delivery via WebSocket without polling.

**Alternatives Considered**:
1. Polling-based WebSocket push
2. PostgreSQL LISTEN/NOTIFY
3. PostgreSQL logical replication with wal2json
4. External message queue (Kafka, RabbitMQ)

**Decision**: Use PostgreSQL logical replication with wal2json plugin.

**Rationale**:
- True real-time delivery (no polling delay)
- Leverages existing PostgreSQL infrastructure
- Reliable delivery via replication protocol
- No additional infrastructure needed

**Consequences**:
- Requires PostgreSQL 17+ with wal2json
- Replication slot must be managed
- More complex Docker setup for development
- WAL retention must be monitored

---

## 2024-03: Implement Infrastructure as Code (IaC)

**Context**: Need declarative way to set up channels, producers, and links at application startup.

**Alternatives Considered**:
1. SQL seed scripts
2. Custom JSON configuration
3. Environment variables
4. Kubernetes ConfigMaps

**Decision**: Use JSON file at `$HOME/.sintropy-engine/init.json` with hash-based change detection.

**Rationale**:
- Simple, portable format
- No Kubernetes dependency
- Hash-based change detection avoids unnecessary work
- Reconciliation handles both additions and deletions

**Consequences**:
- Fixed file location (not configurable)
- SHA-256 hash stored in `iac_files` table
- Deletion order matters (FK constraints)
- Whitespace changes trigger reprocessing

---

## 2024-03: Separate Dead Letter Queue Table

**Context**: Failed messages need to be preserved for inspection and recovery.

**Alternatives Considered**:
1. Status column in messages table (FAILED status)
2. Separate dead_letter_queue table
3. Archive to external storage

**Decision**: Create dedicated `dead_letter_queue` table.

**Rationale**:
- Messages table stays performant (only active messages)
- DLQ can have different indexes for recovery queries
- Clear separation of concerns
- Preserves original message_id for traceability

**Consequences**:
- Trigger moves failed messages automatically
- Recovery requires re-insertion to messages table
- DLQ grows indefinitely (manual cleanup needed)
- `dlq_entry_id` is different from `message_id`

---

## 2024-03: Use Kotlin with Quarkus

**Context**: Need modern JVM framework for building the message broker.

**Alternatives Considered**:
1. Spring Boot with Java
2. Spring Boot with Kotlin
3. Quarkus with Java
4. Quarkus with Kotlin
5. Micronaut with Kotlin

**Decision**: Quarkus with Kotlin.

**Rationale**:
- Fast startup time (important for testing)
- Native compilation option for production
- Excellent Kotlin support
- CDI-based dependency injection
- Extension ecosystem (JOOQ, Flyway)

**Consequences**:
- Team must know Kotlin syntax
- Extension functions for DTOs (`toResponse()`)
- Data classes for immutable domain models
- Coroutines available if needed later

---

## Decision Template

Use this template for new decisions:

```markdown
## YYYY-MM: Decision Title

**Context**: Why this decision was needed

**Alternatives Considered**:
1. Alternative 1
2. Alternative 2
3. Alternative 3

**Decision**: What was decided

**Rationale**:
- Reason 1
- Reason 2
- Reason 3

**Consequences**:
- Consequence 1
- Consequence 2
- Consequence 3
```
