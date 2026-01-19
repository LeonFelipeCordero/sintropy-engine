# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Start local PostgreSQL with wal2json plugin
docker-compose -f development/docker-compose.yaml up -d

# Run in dev mode with live coding
./gradlew quarkusDev -Dapi.version=1.44

# Run all tests
./gradlew test -Dapi.version=1.44

# Run a single test class
./gradlew test --tests "com.ph.sintropyengine.broker.channel.service.ChannelServiceTest"

# Run a single test method
./gradlew test --tests "com.ph.sintropyengine.broker.channel.service.ChannelServiceTest.should create a channel"

# Build the application
./gradlew build
```

## Architecture

Sintropy Engine is a message broker built with Quarkus and Kotlin. It supports two channel types:

- **QUEUE** with STANDARD or FIFO consumption
- **STREAM** for real-time WebSocket delivery

### Domain Structure

```
broker/
├── channel/     # Channel and routing key management
├── producer/    # Message publishers
├── consumption/ # Message polling, DLQ, circuit breakers
└── iac/         # Infrastructure as Code definitions
```

Each domain follows the pattern: `api/` (REST endpoints) → `service/` (business logic) → `repository/` (database
access) → `model/` (domain objects).

### Key Components

- **PostgreSQL Logical Replication**: Uses `wal2json` plugin to stream message inserts to WebSocket consumers in
  real-time
- **Circuit Breaker**: For FIFO channels, when a message fails, the circuit opens and routes subsequent messages to DLQ
- **Advisory Locks**: PostgreSQL `pg_try_advisory_xact_lock` ensures messages are processed by one consumer at a time
- **Database Triggers**: Heavy use of triggers for event logging, DLQ routing, and circuit breaker logic

### Database Migrations

Located in `src/main/resources/db/migration/`:

- **V1.0**: Core schema - `channels` table with `channel_type` enum (QUEUE/STREAM), `routing_keys` table, `queues` table
  with `consumption_type` enum (STANDARD/FIFO)
- **V1.2**: `producers` table - message publishers linked to channels
- **V1.3**: `messages` table with `message_status_type` enum (READY/IN_FLIGHT/FAILED), `message_log` table for event
  sourcing. Triggers: `insert_into_message_log` (copies inserts to log), `mark_as_delivered_in_message_log` (marks
  processed on delete)
- **V1.4**: PostgreSQL publication `messages_pub_insert_only` for logical replication (INSERT only)
- **V1.5**: `channel_links` table - enables message routing between channels
- **V1.6**: Trigger `route_message_after_insert` - automatically copies messages to linked target channels
- **V1.7**: `dead_letter_queue` table. Trigger `auto_delete_on_failed` - when message status changes to FAILED, copies
  to DLQ, deletes from messages and message_log
- **V1.8**: `iac_files` table - tracks Infrastructure as Code file hashes for change detection
- **V1.9**: `channel_circuit_breakers` table with `circuit_state` enum (CLOSED/OPEN). Triggers: auto-create/delete
  circuit breakers with routing keys, `open_circuit_on_failed_delete` - for FIFO channels, opens circuit and moves all
  remaining messages to DLQ when a FAILED message is deleted

## JOOQ Guidelines

- Never use `DSL.field()` or `DSL.table()` - always use generated classes from `Tables.*`
- Insert pattern:

```kotlin
context
    .insertInto(
        Tables.PRODUCERS,
        Tables.PRODUCERS.NAME,
        Tables.PRODUCERS.CHANNEL_ID,
    ).values(producer.name, producer.channelId)
    .returning()
    .fetchOneInto(Producer::class.java)
    ?: throw IllegalStateException("Something went wrong creating a new Producer")
```

- Use `.fetchOneInto(Class)` for single results
- Use `.fetchInto(Class)` for multiple results

## SQL Conventions

- Index naming: `table_field1_field2_idx`
- All tables have `created_at` and `updated_at` timestamps
- JSONB columns use `JSONB.jsonb(string)` for insertion

## Testing

- Tests extend `IntegrationTestBase` which provides helper methods and cleanup
- Uses TestContainers with real PostgreSQL instances
- Test database runs on a random port to avoid conflicts
- When you need a producer with channel use createChannelWithProducer, pass the consumption type

## Kotlin APIs

- When building an API response for an optional object follow a pattern like this one

```kotlin
return circuitBreakerService.getCircuitBreaker(channelName, routingKey)
    ?.let {
        Response.ok(it.toResponse(channelName)).build() 
     ?: return Response.status(Response.Status.NOT_FOUND).entity("Message not found").build()
```

- When getting a channel, if the routing key is available use ChannelService.findByNameAndRoutingKeyStrict(channelName:
  String, routingKey: String)

## Code standards

- Use guard clauses with early returns or early error over nested if statement
- Elvis operator ?: over if(obj == null)
- Use domain exceptions over standard ones

## Documentation

- Every new feature should be added to the agent_context. Choose the file that fits the best

## Code review

- When asked to do code review, write your findings in ./review.md by overriding all existing content in the file.

