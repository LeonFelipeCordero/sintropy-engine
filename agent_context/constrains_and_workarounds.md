# Constraints and Workarounds

## Purpose

This document captures known limitations, technical constraints, gotchas, and their workarounds for Sintropy Engine.

## Invariants

- All workarounds documented here are intentional design decisions
- Performance constraints are measured, not assumed
- Database constraints must be enforced at the schema level, not just application level

---

## Database Constraints

### PostgreSQL Version Requirement
- **Constraint**: PostgreSQL 17+ required
- **Reason**: Logical replication with `wal2json` plugin for WebSocket streaming
- **Impact**: Cannot use older PostgreSQL versions or other databases

### JOOQ Code Generation
- **Constraint**: Generated classes must not be manually edited
- **Location**: `src/main/kotlin-gen/`
- **Workaround**: Run `./gradlew generateJooq` after schema changes
- **Gotcha**: Database must be running for code generation

### Enum Type Casts
- **Constraint**: PostgreSQL enums need implicit VARCHAR casts for JOOQ
- **Reason**: JOOQ sends enum values as strings
- **Solution**: Each enum type has a corresponding cast defined in migrations
```sql
CREATE CAST (VARCHAR AS channel_type) WITH INOUT AS IMPLICIT;
```

### Advisory Lock Hash Collisions
- **Constraint**: Advisory locks use Java `hashCode()` which can collide
- **Workaround**: Lock hash is scoped to channel + routing key combination
- **Risk**: Extremely low probability of collision in practice
- **Impact**: Worst case is temporary message delivery delay, not data corruption

---

## API Constraints

### JSONB Field Handling
- **Constraint**: Message and headers fields must be valid JSON strings
- **Gotcha**: API expects stringified JSON, not raw JSON objects
- **Example**:
```json
{
  "message": "{\"key\": \"value\"}",  // Correct
  "message": {"key": "value"}         // Incorrect
}
```

### Channel Name Uniqueness
- **Constraint**: Channel names are globally unique
- **Impact**: Cannot have two channels with the same name, even of different types
- **Workaround**: Use naming conventions like `orders-queue`, `orders-stream`

### Producer-Channel Binding
- **Constraint**: A producer is permanently bound to one channel
- **Impact**: Cannot reassign a producer to a different channel
- **Workaround**: Delete and recreate the producer

### Routing Key Scoping
- **Constraint**: Routing keys are unique within a channel, not globally
- **Impact**: Same routing key name can exist on different channels
- **Gotcha**: When querying, always specify both channel and routing key

---

## Consumption Constraints

### STANDARD Queue Retry Limits
- **Constraint**: Maximum 4 delivery attempts, 15-minute timeout
- **Values are hardcoded**: No configuration option to change these
- **Location**: `MessageRepository.pollFromStandardChannelByRoutingKey()`
- **Workaround**: If different retry behavior needed, use FIFO with manual retry logic

### FIFO Single Message In-Flight
- **Constraint**: Only one message can be IN_FLIGHT per routing key
- **Impact**: Polling returns empty if any message is being processed
- **Gotcha**: Long-running consumers block the entire routing key
- **Workaround**: Use shorter processing times or STANDARD queues for parallel processing

### Circuit Breaker Cascade Effect
- **Constraint**: Circuit opening moves ALL remaining messages to DLQ
- **Impact**: One bad message can affect many good messages
- **Workaround**:
  1. Fix the root cause
  2. Close circuit with `recover=true` to restore messages
  3. Messages will be reprocessed in order

### Abandoned STANDARD Messages
- **Constraint**: After 4 failed deliveries, messages stay IN_FLIGHT forever
- **Impact**: Messages are not auto-deleted or moved to DLQ
- **Workaround**: Manual cleanup or monitoring for stuck messages
- **Query to find abandoned**:
```sql
SELECT * FROM messages
WHERE status = 'IN_FLIGHT'
AND delivered_times >= 4
AND last_delivered < now() - interval '15 minutes';
```

---

## Channel Link Constraints

### Ordering Guarantee Enforcement
- **Constraint**: STANDARD queues cannot link to FIFO/Stream targets
- **Reason**: STANDARD queues don't preserve message ordering
- **Error**: "Cannot link Standard Queue to FIFO/Stream: Standard Queues don't guarantee message ordering"
- **Workaround**: Use FIFO source or change target to STANDARD

### No Circular Links
- **Constraint**: Messages with `origin_message_id` don't trigger routing
- **Reason**: Prevents infinite message loops
- **Impact**: A → B → A routing is safe (won't loop)
- **Gotcha**: Original message ID is preserved through routing chain

---

## WebSocket Streaming Constraints

### Logical Replication Setup
- **Constraint**: Requires PostgreSQL logical replication configuration
- **Setup Required**:
  - `wal_level = logical` in postgresql.conf
  - `wal2json` plugin installed
  - Replication slot created
- **Docker**: Development docker-compose handles this automatically

### Stream vs Queue Polling
- **Constraint**: Streams use WebSocket, not HTTP polling
- **Impact**: Cannot use `/queues/poll` endpoint for STREAM channels
- **Workaround**: Connect via WebSocket at `/ws/streaming/{channel}/{routingKey}`

---

## Testing Constraints

### TestContainers Random Port
- **Constraint**: Test PostgreSQL runs on random port
- **Reason**: Avoid port conflicts in parallel test execution
- **Impact**: Cannot connect to test database externally during tests
- **Location**: `PostgresqlDBTestResource.kt`

### Test Data Isolation
- **Constraint**: Each test class may share test data
- **Workaround**: Use `IntegrationTestBase.clean()` between tests
- **Gotcha**: Don't rely on specific IDs or order of test execution

### Integration Test Only
- **Constraint**: All tests require real PostgreSQL database
- **Reason**: Heavy reliance on triggers and advisory locks
- **Impact**: No unit tests, slower test execution
- **Workaround**: Use `createChannelWithProducer()` helper for quick setup

---

## IaC Constraints

### File Location Fixed
- **Constraint**: IaC file must be at `$HOME/.sintropy-engine/init.json`
- **Impact**: Cannot use different paths in production
- **Workaround for Testing**: `IaCService.processIaCFile(basePath)` accepts custom path

### Hash-Based Change Detection
- **Constraint**: Any change to init.json triggers full reconciliation
- **Impact**: Even whitespace changes cause re-processing
- **Gotcha**: Order of JSON fields matters for hash calculation

### Deletion Order Matters
- **Constraint**: IaC deletes in specific order: links → producers → channels
- **Reason**: Foreign key constraints
- **Impact**: Cannot delete channel that has producers pointing to it

---

## Performance Considerations

### Index on Messages Table
```sql
CREATE INDEX messages_polling_idx ON messages
  (channel_id, routing_key, status, last_delivered, delivered_times);
```
- **Purpose**: Optimize polling queries
- **Gotcha**: Large message tables may need VACUUM/REINDEX periodically

### Message Log Growth
- **Constraint**: `message_log` table grows indefinitely
- **Impact**: Need periodic archival or cleanup strategy
- **Gotcha**: `processed = false` entries indicate messages that never succeeded

### DLQ Table Growth
- **Constraint**: DLQ entries are not auto-deleted
- **Impact**: Manual cleanup required after recovery
- **Query for old entries**:
```sql
DELETE FROM dead_letter_queue WHERE failed_at < now() - interval '30 days';
```

---

## Common Gotchas Summary

| Gotcha | Impact | Solution |
|--------|--------|----------|
| JOOQ: Don't use `DSL.field()` | Runtime errors | Use `Tables.*` classes |
| JSONB: Must be stringified | Parse errors | `JSONB.jsonb(string)` |
| FIFO: Circuit opens on ANY failure | All messages to DLQ | Use STANDARD for fault tolerance |
| STANDARD: Max 4 retries hardcoded | Messages stuck IN_FLIGHT | Monitor and clean up |
| Routing key: Scoped to channel | Wrong results | Always specify channel + key |
| Channel deletion: Cascade deletes data | Data loss | Backup before deletion |
| IaC: Whitespace changes hash | Unnecessary reprocessing | Use consistent formatting |
