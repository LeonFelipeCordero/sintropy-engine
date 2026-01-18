# Database

## Purpose

This document defines the database schema, triggers, indexes, and migrations for Sintropy Engine. PostgreSQL 17+ with `wal2json` plugin is required.

## Invariants

- All tables have `created_at` and `updated_at` timestamptz columns (default `now()`)
- UUIDs are generated via `gen_random_uuid()` for all primary keys
- Foreign keys use `ON DELETE CASCADE` for routing_keys, channel_links, channel_circuit_breakers
- Enum types have implicit VARCHAR cast for JOOQ compatibility
- Triggers execute in order: BEFORE triggers first, then AFTER triggers
- message_log entries are never deleted by the application (event sourcing)

## Constraints

- Index naming convention: `table_field1_field2_idx`
- Composite primary keys used for: routing_keys, message_log
- Unique constraints on: channels.name, producers.name, iac_files.file_name
- Channel links have unique constraint on (source_channel_id, target_channel_id, source_routing_key, target_routing_key)

---

## Schema Overview

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│    channels     │     │   routing_keys  │     │     queues      │
├─────────────────┤     ├─────────────────┤     ├─────────────────┤
│ channel_id (PK) │◄────│ channel_id (FK) │     │ channel_id (PK) │
│ name (unique)   │     │ routing_key     │     │ consumption_type│
│ channel_type    │     │ (composite PK)  │     └────────┬────────┘
└────────┬────────┘     └─────────────────┘              │
         │                                               │
         │              ┌─────────────────┐              │
         └──────────────│   producers     │◄─────────────┘
                        ├─────────────────┤
                        │ producer_id (PK)│
                        │ name (unique)   │
                        │ channel_id (FK) │
                        └────────┬────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│    messages     │     │   message_log   │     │ dead_letter_queue│
├─────────────────┤     ├─────────────────┤     ├─────────────────┤
│ message_id (PK) │────▶│ message_id      │     │ dlq_entry_id(PK)│
│ channel_id (FK) │     │ timestamp       │     │ message_id      │
│ producer_id (FK)│     │ channel_id      │     │ channel_id (FK) │
│ routing_key     │     │ (composite PK)  │     │ producer_id (FK)│
│ message (JSONB) │     │ processed       │     │ failed_at       │
│ headers (JSONB) │     └─────────────────┘     └─────────────────┘
│ status          │
│ delivered_times │
│ origin_msg_id   │
└─────────────────┘

┌─────────────────┐     ┌─────────────────────────┐
│  channel_links  │     │ channel_circuit_breakers│
├─────────────────┤     ├─────────────────────────┤
│ channel_link_id │     │ circuit_id (PK)         │
│ source_chan_id  │     │ channel_id (FK)         │
│ target_chan_id  │     │ routing_key             │
│ source_rk       │     │ state (CLOSED/OPEN)     │
│ target_rk       │     │ opened_at               │
│ enabled         │     │ failed_message_id       │
└─────────────────┘     └─────────────────────────┘

┌─────────────────┐
│    iac_files    │
├─────────────────┤
│ file_id (PK)    │
│ file_name (uniq)│
│ hash            │
└─────────────────┘
```

---

## Tables

### channels
```sql
CREATE TABLE channels (
    channel_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(256) NOT NULL,
    channel_type channel_type NOT NULL,  -- ENUM: 'QUEUE', 'STREAM'
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX channels_name_idx ON channels (name);
```

### routing_keys
```sql
CREATE TABLE routing_keys (
    routing_key VARCHAR(128) NOT NULL,
    channel_id  UUID NOT NULL REFERENCES channels (channel_id),
    PRIMARY KEY (routing_key, channel_id)
);
```

### queues
```sql
CREATE TABLE queues (
    channel_id       UUID PRIMARY KEY REFERENCES channels (channel_id),
    consumption_type consumption_type NOT NULL  -- ENUM: 'STANDARD', 'FIFO'
);
```

### producers
```sql
CREATE TABLE producers (
    producer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(128) NOT NULL,
    channel_id  UUID NOT NULL REFERENCES channels (channel_id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX producers_name_idx ON producers (name);
CREATE INDEX producers_channel_id_idx ON producers (channel_id);
```

### messages
```sql
CREATE TABLE messages (
    message_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    origin_message_id UUID,
    timestamp         TIMESTAMPTZ NOT NULL DEFAULT now(),
    channel_id        UUID NOT NULL REFERENCES channels (channel_id),
    producer_id       UUID NOT NULL REFERENCES producers (producer_id),
    routing_key       VARCHAR(128) NOT NULL,
    message           JSONB NOT NULL,
    headers           JSONB NOT NULL,
    status            message_status_type NOT NULL DEFAULT 'READY',  -- ENUM: 'READY', 'IN_FLIGHT', 'FAILED'
    last_delivered    TIMESTAMPTZ,
    delivered_times   INT4 NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX messages_polling_idx ON messages (channel_id, routing_key, status, last_delivered, delivered_times);
CREATE INDEX messages_origin_message_idx ON messages (origin_message_id);
```

### message_log
```sql
CREATE TABLE message_log (
    message_id        UUID NOT NULL,
    origin_message_id UUID,
    timestamp         TIMESTAMPTZ NOT NULL,
    channel_id        UUID NOT NULL REFERENCES channels (channel_id),
    producer_id       UUID NOT NULL REFERENCES producers (producer_id),
    routing_key       VARCHAR(128) NOT NULL,
    message           JSONB NOT NULL,
    headers           JSONB NOT NULL,
    processed         BOOL NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (message_id, timestamp, channel_id)
);
```

### dead_letter_queue
```sql
CREATE TABLE dead_letter_queue (
    dlq_entry_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    timestamp         TIMESTAMPTZ NOT NULL DEFAULT now(),
    channel_id        UUID NOT NULL REFERENCES channels (channel_id),
    producer_id       UUID NOT NULL REFERENCES producers (producer_id),
    routing_key       VARCHAR(128) NOT NULL,
    message           JSONB NOT NULL,
    headers           JSONB NOT NULL,
    origin_message_id UUID,
    delivered_times   INT4 NOT NULL,
    failed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX dlq_channel_routing_idx ON dead_letter_queue (channel_id, routing_key);
CREATE INDEX dlq_message_id_idx ON dead_letter_queue (message_id);
CREATE INDEX dlq_failed_at_idx ON dead_letter_queue (failed_at);
```

### channel_links
```sql
CREATE TABLE channel_links (
    channel_link_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_channel_id UUID NOT NULL REFERENCES channels(channel_id) ON DELETE CASCADE,
    target_channel_id UUID NOT NULL REFERENCES channels(channel_id) ON DELETE CASCADE,
    source_routing_key VARCHAR(255) NOT NULL,
    target_routing_key VARCHAR(255) NOT NULL,
    enabled           BOOLEAN DEFAULT true,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now(),
    UNIQUE(source_channel_id, target_channel_id, source_routing_key, target_routing_key)
);
CREATE INDEX idx_channel_links_source ON channel_links(source_channel_id, source_routing_key);
CREATE INDEX idx_channel_links_target ON channel_links(target_channel_id);
```

### channel_circuit_breakers
```sql
CREATE TABLE channel_circuit_breakers (
    circuit_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id        UUID NOT NULL REFERENCES channels (channel_id) ON DELETE CASCADE,
    routing_key       VARCHAR(128) NOT NULL,
    state             circuit_state NOT NULL DEFAULT 'CLOSED',  -- ENUM: 'CLOSED', 'OPEN'
    opened_at         TIMESTAMPTZ,
    failed_message_id UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (channel_id, routing_key)
);
CREATE INDEX idx_circuit_breakers_channel_routing ON channel_circuit_breakers (channel_id, routing_key);
CREATE INDEX idx_circuit_breakers_state ON channel_circuit_breakers (state);
```

### iac_files
```sql
CREATE TABLE iac_files (
    file_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name  VARCHAR(256) NOT NULL,
    hash       VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX iac_files_file_name_idx ON iac_files (file_name);
```

---

## Enum Types

```sql
CREATE TYPE channel_type AS ENUM ('QUEUE', 'STREAM');
CREATE TYPE consumption_type AS ENUM ('STANDARD', 'FIFO');
CREATE TYPE message_status_type AS ENUM ('READY', 'IN_FLIGHT', 'FAILED');
CREATE TYPE circuit_state AS ENUM ('CLOSED', 'OPEN');

-- Implicit casts for JOOQ compatibility
CREATE CAST (VARCHAR AS channel_type) WITH INOUT AS IMPLICIT;
CREATE CAST (VARCHAR AS consumption_type) WITH INOUT AS IMPLICIT;
CREATE CAST (VARCHAR AS message_status_type) WITH INOUT AS IMPLICIT;
CREATE CAST (VARCHAR AS circuit_state) WITH INOUT AS IMPLICIT;
```

---

## Triggers

### insert_into_message_log (AFTER INSERT on messages)
Copies every new message to message_log for event sourcing.
```sql
CREATE TRIGGER insert_into_message_log
    AFTER INSERT ON messages
    FOR EACH ROW
    EXECUTE FUNCTION messages_to_message_log();
```

### mark_as_delivered_in_message_log (AFTER DELETE on messages)
Marks message_log entry as processed when message is successfully deleted (not FAILED).
```sql
CREATE TRIGGER mark_as_delivered_in_message_log
    AFTER DELETE ON messages
    FOR EACH ROW
    EXECUTE FUNCTION mark_message_log_item_as_processed();
```

### route_message_after_insert (AFTER INSERT on messages)
Copies message to linked target channels (only for original messages, not routed copies).
```sql
CREATE TRIGGER route_message_after_insert
    AFTER INSERT ON messages
    FOR EACH ROW
    EXECUTE FUNCTION route_message_to_linked_channels();
```

### auto_delete_on_failed (AFTER UPDATE on messages)
When status changes to FAILED: copies to DLQ, deletes from messages and message_log.
```sql
CREATE TRIGGER auto_delete_on_failed
    AFTER UPDATE OF status ON messages
    FOR EACH ROW
    WHEN (NEW.status = 'FAILED')
    EXECUTE FUNCTION route_message_to_dql();
```

### create_circuit_breaker_on_routing_key_insert (AFTER INSERT on routing_keys)
Auto-creates circuit breaker record when routing key is added.
```sql
CREATE TRIGGER create_circuit_breaker_on_routing_key_insert
    AFTER INSERT ON routing_keys
    FOR EACH ROW
    EXECUTE FUNCTION create_circuit_breaker_for_routing_key();
```

### delete_circuit_breaker_on_routing_key_delete (AFTER DELETE on routing_keys)
Cleans up circuit breaker when routing key is removed.
```sql
CREATE TRIGGER delete_circuit_breaker_on_routing_key_delete
    AFTER DELETE ON routing_keys
    FOR EACH ROW
    EXECUTE FUNCTION delete_circuit_breaker_for_routing_key();
```

### open_circuit_on_failed_delete (BEFORE DELETE on messages)
For FIFO channels: opens circuit breaker and moves ALL remaining messages to DLQ.
```sql
CREATE TRIGGER open_circuit_on_failed_delete
    BEFORE DELETE ON messages
    FOR EACH ROW
    EXECUTE FUNCTION open_circuit_on_failed_message_delete();
```

---

## Publications (Logical Replication)

```sql
CREATE PUBLICATION messages_pub_insert_only
    FOR TABLE messages
    WITH (publish = 'insert');
```

Used by WebSocket streaming to receive real-time message notifications via `wal2json`.

---

## Key Queries

### STANDARD Queue Polling
```sql
WITH result AS (
    SELECT message_id
    FROM messages
    WHERE channel_id = :channelId
      AND routing_key = :routingKey
      AND (status = 'READY' OR
           (status = 'IN_FLIGHT' AND
            last_delivered < now() - interval '15 minutes' AND
            delivered_times < 4))
      AND pg_try_advisory_xact_lock(:hash)
    ORDER BY timestamp
    LIMIT :pollingCount
    FOR UPDATE SKIP LOCKED
)
UPDATE messages
SET status = 'IN_FLIGHT',
    last_delivered = now(),
    delivered_times = delivered_times + 1,
    updated_at = now()
FROM result
WHERE messages.message_id IN (result.message_id)
RETURNING messages.*;
```

### FIFO Queue Polling
```sql
WITH result AS (
    SELECT message_id
    FROM messages
    WHERE channel_id = :channelId
      AND routing_key = :routingKey
      AND status = 'READY'
      AND NOT EXISTS (
          SELECT 1 FROM messages
          WHERE channel_id = :channelId
            AND routing_key = :routingKey
            AND status = 'IN_FLIGHT'
      )
      AND pg_try_advisory_xact_lock(:hash)
    ORDER BY timestamp
    LIMIT :pollingCount
    FOR UPDATE SKIP LOCKED
)
UPDATE messages
SET status = 'IN_FLIGHT',
    last_delivered = now(),
    delivered_times = delivered_times + 1,
    updated_at = now()
FROM result
WHERE messages.message_id IN (result.message_id)
RETURNING messages.*;
```

---

## Migrations

| Version | File | Description |
|---------|------|-------------|
| V1.0 | create-channels-table.sql | channels, routing_keys, queues tables with enums |
| V1.2 | create-producers-table.sql | producers table |
| V1.3 | create-messages-table.sql | messages, message_log tables with triggers |
| V1.4 | create-publication.sql | Logical replication publication for streaming |
| V1.5 | create-channel-links-table.sql | channel_links table |
| V1.6 | add-message-routing.sql | Message routing trigger for channel links |
| V1.7 | create-dead-letter-queue.sql | dead_letter_queue table with auto-delete trigger |
| V1.8 | create-iac-table.sql | iac_files table for IaC tracking |
| V1.9 | create-channel-circuit-breaker.sql | channel_circuit_breakers table with triggers |

---

## JOOQ Guidelines

- Never use `DSL.field()` or `DSL.table()` - always use generated classes from `Tables.*`
- Insert pattern: `.insertInto(TABLE, FIELD1, FIELD2).values(v1, v2).returning().fetchOneInto(Class)`
- Use `.fetchOneInto(Class)` for single results
- Use `.fetchInto(Class)` for multiple results
- JSONB columns use `JSONB.jsonb(string)` for insertion
