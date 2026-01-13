# Sintropy Engine Project Documentation

This document provides a comprehensive overview of the Sintropy Engine, detailing its functional and technical requirements based on an end-to-end analysis of the codebase.

## FUNCTIONAL REQUIREMENTS

### 1. Channel Management

The system provides robust capabilities for managing communication channels, which serve as the fundamental entities for message exchange.

*   **Channel Creation:** Users can create new channels by specifying a unique `name`, a `channelType` (either `QUEUE` or `STREAM`), and a list of `routingKeys`. For `QUEUE` type channels, a `consumptionType` (`STANDARD` or `FIFO`) must also be provided.
    *   **Validation:** Channel names must be unique. At least one routing key is required for channel creation.
*   **Channel Retrieval:** Channels can be retrieved by their unique `channelId` (UUID) or by their `name`.
*   **Channel Deletion:** Existing channels can be deleted using their `channelId`.
*   **Routing Key Management:** Additional `routingKeys` can be added to an existing channel. The system prevents the addition of duplicate routing keys.

### 2. Producer Management

Producers are entities responsible for sending messages to channels.

*   **Producer Creation:** Producers can be created by providing a `name` and associating them with an existing `channelName`.
*   **Producer Retrieval:** Producers can be retrieved by their unique `producerId` (UUID) or by the `channelName` they are associated with.
*   **Producer Deletion:** Existing producers can be deleted using their `producerId`.
*   **Message Publishing:** Producers can publish messages to a channel.
    *   **Validation:** The system ensures that the target channel exists and that the message's `routingKey` is valid for that channel before publishing.

### 3. Consumer Management and WebSocket Streaming

Consumers are entities that receive messages from channels. The system primarily supports consumer interaction via WebSockets for real-time streaming.

*   **WebSocket Connection:** Consumers establish a WebSocket connection to a specific endpoint (`/ws/streaming/{channelName}/{routingKey}`).
*   **Dynamic Consumer Registration:** Upon a successful WebSocket connection, the consumer is dynamically registered with the system's `ConnectionRouter`, associating its `connectionId` with the specified `channelName` and `routingKey`.
*   **Real-time Message Delivery:** Messages published to a channel are streamed in real-time to all connected WebSocket consumers that match the message's `channelId` and `routingKey`.
*   **Consumer Disconnection:** When a WebSocket connection is closed, the consumer is automatically unregistered from the `ConnectionRouter`.
*   **Ephemeral Consumer State:** Consumer state (e.g., `connectionId`) is primarily managed in-memory by the `ConnectionRouter`, indicating that active consumers are not persistently stored in the database.

### 4. Message Handling

The core functionality revolves around the creation, storage, and processing of messages.

*   **Message Structure:** Messages encapsulate a `messageId` (UUID), `timestamp`, `channelId`, `producerId`, `routingKey`, a `message` payload (JSONB), `headers` (JSONB), a `status` (`READY`, `IN_FLIGHT`, `FAILED`), `lastDelivered` timestamp, and `deliveredTimes` count.
*   **Message Persistence:** All published messages are stored in the database.
*   **Message Status Tracking:** The `status` field allows tracking the lifecycle of a message through its processing stages.
*   **Event Logging:** Every message insertion into the `messages` table automatically triggers the creation of an immutable `event_log` entry. This provides a historical record of all messages. When a message is dequeued (deleted), the corresponding `event_log` entry is marked as `processed`.

### 5. Message Polling (for Queue Channels)

For `QUEUE` type channels, messages can be actively polled by clients.

*   **Standard Queue Polling:**
    *   Consumers can poll a specified number of messages (`pollingCount`) from a standard queue for a given `channelId` and `routingKey`.
    *   Messages are retrieved if their status is `READY` or if they are `IN_FLIGHT` but have exceeded a delivery timeout (15 minutes) and have not exceeded a maximum retry count (4 times).
    *   Polled messages are immediately marked as `IN_FLIGHT`, their `lastDelivered` timestamp is updated, and `deliveredTimes` is incremented.
    *   Concurrency control is implemented using PostgreSQL advisory locks (`pg_try_advisory_xact_lock`) and `FOR UPDATE SKIP LOCKED` to ensure that messages are processed by only one consumer at a time and to handle concurrent polling efficiently.
*   **FIFO Queue Polling:**
    *   Consumers can poll messages from a FIFO queue for a given `channelId` and `routingKey`.
    *   Messages are retrieved only if their status is `READY` and there are no other messages for the same `channelId` and `routingKey` currently `IN_FLIGHT`. This ensures strict FIFO ordering.
    *   Similar concurrency control mechanisms (advisory locks, `FOR UPDATE SKIP LOCKED`) are applied.
*   **Message Acknowledgment/Failure:**
    *   **Dequeue:** Messages can be explicitly dequeued (deleted) by their `messageId` after successful processing. The system prevents dequeuing messages that are still in `READY` status.
    *   **Mark as Failed:** Messages can be marked as `FAILED` by their `messageId`, indicating that they could not be processed successfully.

### 6. PostgreSQL Logical Replication

The system leverages PostgreSQL's logical replication feature for real-time message streaming to WebSocket consumers.

*   **Insert-Only Publication:** A PostgreSQL publication (`messages_pub_insert_only`) is configured to publish only `INSERT` operations on the `messages` table.
*   **`wal2json` Output Plugin:** The replication consumer uses the `wal2json` PostgreSQL output plugin to receive changes in a structured JSON format.
*   **Real-time Change Capture:** The system continuously consumes the replication stream, parses the JSON output, and extracts new message data.
*   **Dynamic Slot Management:** The replication consumer manages a replication slot ("messages_slot"), attempting to drop and recreate it to ensure a clean and consistent replication state.

### 7. Configuration and Feature Flags

The application's behavior can be customized through external configuration.

*   **Database Configuration:** Database connection details (JDBC URL, username, password) are configurable.
*   **Feature Toggles:** A feature flag (`syen.feature-flags.with-full-replication`) allows enabling or disabling the full PostgreSQL logical replication consumer, providing flexibility for different deployment environments or testing scenarios.
*   **Custom JSON Mapping:** The system uses a custom JSON object mapper, allowing fine-grained control over how Java objects are serialized to and deserialized from JSON, including specific handling for date/time formats and `JSONB` types.

---

## TECHNICAL REQUIREMENTS

### 3. Data Model

The database schema is designed to support the messaging system's requirements.

*   **Enums:**
    *   `channel_type`: `QUEUE`, `STREAM`
    *   `consumption_type`: `STANDARD`, `FIFO`
    *   `message_status_type`: `READY`, `IN_FLIGHT`, `FAILED`
*   **Tables:**
    *   `channels`: Stores channel metadata (`channel_id`, `name`, `channel_type`, `created_at`, `updated_at`).
    *   `routing_keys`: Associates `routing_key` with `channel_id`.
    *   `queues`: Stores `consumption_type` for `QUEUE` type channels, linked to `channel_id`.
    *   `consumers`: Stores consumer details (`consumer_id`, `channel_id`, `routing_key`, `created_at`, `updated_at`). (Note: This table exists in the schema but its corresponding repository and service methods are largely commented out, suggesting in-memory management for active consumers).
    *   `producers`: Stores producer details (`producer_id`, `name`, `channel_id`, `created_at`, `updated_at`).
    *   `messages`: Stores message content and metadata (`message_id`, `timestamp`, `channel_id`, `producer_id`, `routing_key`, `message` (JSONB), `headers` (JSONB), `status`, `last_delivered`, `delivered_times`, `created_at`, `updated_at`).
    *   `event_log`: An immutable log of message insertions (`message_id`, `timestamp`, `channel_id`, `producer_id`, `routing_key`, `message` (JSONB), `headers` (JSONB), `processed`, `created_at`, `updated_at`).
*   **Triggers:**
    *   `insert_into_event_log`: `AFTER INSERT` on `messages` to populate `event_log`.
    *   `mark_as_deliver_in_event_log`: `AFTER DELETE` on `messages` to mark `event_log` entries as `processed`.
*   **Publication:** `messages_pub_insert_only` for logical replication of `INSERT` operations on the `messages` table.

### 4. API Endpoints

The system exposes both RESTful HTTP endpoints and WebSocket endpoints.

*   **REST Endpoints:**
    *   `/channels`: CRUD operations for channels.
    *   `/producers`: CRUD operations for producers and message publishing.
    *   `/queues`: Polling operations for queue channels, marking messages as failed, and dequeuing messages.
*   **WebSocket Endpoints:**
    *   `/ws/streaming/{channelName}/{routingKey}`: For real-time message streaming to consumers.

### 5. Database Interaction

*   **Raw SQL:** Complex polling logic for `STANDARD` and `FIFO` queues is implemented using embedded raw SQL queries within the system to leverage advanced PostgreSQL features like advisory locks and `FOR UPDATE SKIP LOCKED`.
*   **Concurrency Control:**
    *   `pg_try_advisory_xact_lock`: Used in polling queries to acquire advisory locks, preventing race conditions and ensuring that only one consumer processes a specific set of messages at a time.
    *   `FOR UPDATE SKIP LOCKED`: Optimizes concurrent polling by allowing transactions to skip rows that are currently locked by other transactions, improving throughput.

### 6. Message Processing Flow

1.  **Producer Publishes Message:** A producer sends a message to the `/producers/messages` REST endpoint.
2.  **Message Persistence:** The `ProducerService` validates the message and saves it to the `messages` table via the `MessageRepository`.
3.  **Event Log Creation:** An `AFTER INSERT` trigger automatically creates an entry in the `event_log` table.
4.  **Replication Stream Capture:** The `PGReplicationController` consumes the PostgreSQL logical replication stream, capturing the `INSERT` event for the new message.
5.  **Message Routing:** The `PGReplicationController` uses the `ConnectionRouter` to identify all active WebSocket consumers subscribed to the message's `channelId` and `routingKey`.
6.  **Real-time Delivery:** The message is serialized to JSON and broadcast to the identified WebSocket consumers.
7.  **Consumer Polling (for Queues):** For queue channels, consumers can explicitly poll messages via the `/queues/poll` REST endpoint.
8.  **Message State Update:** Polled messages are marked `IN_FLIGHT`. Upon successful processing, consumers can `dequeue` the message (deleting it from the `messages` table, which marks the `event_log` entry as `processed`). If processing fails, the message can be marked `FAILED`.

### 7. Replication Mechanism

The `PGReplicationController` is the central component for handling PostgreSQL logical replication:

*   It initializes a `PGReplicationStream` using the `wal2json` plugin.
*   It continuously reads pending changes from the replication stream.
*   It parses the JSON output from `wal2json` to extract message details.
*   It maps the extracted data to `Message` objects.
*   It then uses the `ConnectionRouter` to fan out these messages to relevant WebSocket consumers.
*   It manages the replication slot, ensuring its existence and proper configuration.

### 8. Configuration Management

*   **SmallRye Config:** Configuration properties are defined using SmallRye Config's `@ConfigMapping` interfaces, providing type-safe access to settings.
*   **`application.yml`:** Configuration values are typically provided in `application.yml` (or other Quarkus-supported configuration sources).
*   **Custom ObjectMapper:** A `CustomObjectMapper` is configured to handle specific JSON serialization/deserialization requirements, including date/time formats and `JSONB` types.

### 9. Utility Functions

*   **`Patterns` object:** Provides extension functions to generate consistent routing strings (`channelId|routingKey`) for various domain models, simplifying internal routing logic.
*   **`TimeUtils`:** Offers utility for handling `OffsetDateTime` objects, including conversion to a default local time zone.

