# NOTES

- Add defaults to all tables
- solve associations between channel message routing key
- Add the option to add new routing keys to channels
- Add extra migration steps to compleate the message and even log

  `pollFromStandardChannelByRoutingKey`:

- Selects messages with status READY.
- Includes retry logic for "stale" IN_FLIGHT messages (older than 15 minutes, delivered less than 4 times).
- Orders by timestamp, but without a strict guarantee across multiple consumers.

  `pollFromFifoChannelByRoutingKey`:

- Selects messages with status READY only if no other messages for that channel and routing key are IN_FLIGHT. This enforces strict ordering.
- It does not include retry logic for stale messages to maintain FIFO order; markAsFailed is likely used instead.
- Orders by timestamp to ensure FIFO since only one consumer can process messages at a time for a given routing key.

  With this, my analysis is complete. I will now provide a comprehensive summary of the system's validation and logic to aid in its migration.I've analyzed the polling logic in
  MessageRepository.kt, noting both standard and FIFO queue implementations utilize advisory locks and FOR UPDATE SKIP LOCKED for concurrency control. Standard queues feature retry
  logic for "stuck" IN_FLIGHT messages, while FIFO queues strictly process READY messages only when no other messages for that queue are IN_FLIGHT to ensure order. I have sufficient
  information to provide a comprehensive answer and will now synthesize my findings.Here is a complete summary of the validations, controls, and logic for migration.

  Summary of Findings

  This document outlines the core business logic, validations, and database interactions of the Sintropy Engine, providing a guide for migrating it to another programming language.

  ---

  1. Channel Creation

- Endpoint: POST /channels
- Request Body:
  - name: String
  - channelType: String (Enum: QUEUE or STREAM)
  - routingKeys: List<String>
  - consumptionType: String (Enum: STANDARD or FIFO, required for QUEUE channels)
- Validation Logic:
       1. Unique Name: The channel name must be unique across all channels (enforced by channels_name_idx unique index in the database).
       2. Routing Keys Required: At least one routing key must be provided in the routingKeys list.
       3. Consumption Type for Queues: If channelType is QUEUE, the consumptionType field is mandatory.

  ---

  2. Producer Creation

- Endpoint: POST /producers
- Request Body:
  - name: String
  - channelName: String
- Validation Logic:
       1. Unique Name: The producer name must be unique across all producers (enforced by producers_name_idx unique index).
       2. Channel Exists: The specified channelName must correspond to an existing channel.

  ---

  3. Message Publishing

- Endpoint: POST /producers/messages
- Request Body:
  - channelName: String
  - producerName: String
  - routingKey: String
  - message: String (JSON)
  - headers: String (JSON)
- Validation Logic:
       1. Channel Exists: The specified channelName must exist.
       2. Producer Exists: The specified producerName must exist.
       3. Valid Routing Key: The routingKey must be one of the registered routing keys for the specified channelName.
- Internal Mechanism:
  - A new message is inserted into the messages table with a status of READY.
  - A database trigger (messages_to_event_log) immediately copies the new message into the event_log table for auditing.
  - A PostgreSQL publication (messages_pub_insert_only) captures this INSERT event for logical replication, which is used by STREAM channels.

  ---

  4. Message Consumption

  The system has two distinct consumption models based on the channelType.

  4.1. Streaming Consumption (STREAM channels)

  This model uses logical replication for near real-time, push-based message delivery.

- Mechanism:
       1. Connection: Consumers establish a persistent WebSocket connection to /ws/streaming/{channelName}/{routingKey}.
       2. Subscription: The ConnectionRouter service registers the connection and its interest in the specified routing key.
       3. Replication: The PGRelicationController listens to the logical replication feed from the messages table.
       4. Dispatch: Upon a new message INSERT, the controller receives the data, finds all subscribed WebSocket connections via the ConnectionRouter, and pushes the message to them.
       5. Dequeue: After pushing the message, the controller immediately deletes the message from the messages table.

  4.2. Polling Consumption (QUEUE channels)

  This model uses traditional client-side polling with specific logic for STANDARD and FIFO queues.

- Polling Endpoint: POST /queues/poll
- Polling Query Logic (`MessageRepository.kt`): The core of the polling mechanism lies in two sophisticated SQL queries that perform the following actions atomically:
       1. Select a batch of available messages.
       2. Lock the selected rows to prevent other transactions from picking them up (FOR UPDATE SKIP LOCKED).
       3. Update the status of the selected messages to IN_FLIGHT.
       4. Return the selected messages.

- `STANDARD` Queue Logic (`pollFromStandardChannelByRoutingKey`):
  - Selects messages with status = 'READY'.
  - Includes retry/error handling: It also selects messages that are IN_FLIGHT if they have been in that state for more than 15 minutes and have been delivered fewer than 4
         times. This automatically handles timed-out messages.

- `FIFO` Queue Logic (`pollFromFifoChannelByRoutingKey`):
  - Selects messages with status = 'READY'.
  - Enforces strict ordering: It will not select any messages if there is already a message with status = 'IN_FLIGHT' for the same channel and routing key, guaranteeing that
         only one message is processed at a time in the order it was received.

- Concurrency Control: Both polling queries use pg_try_advisory_xact_lock on a hash of the channel and routing key. This ensures that only one consumer can be polling from a
     specific queue at any given moment, preventing race conditions.

  ---

  5. Message Acknowledgement (for Polled Messages)

  Once a client finishes processing a message from a QUEUE, it must inform the engine.

- Successful Processing:
  - Endpoint: DELETE /queues/messages/{messageId}
  - Action: Deletes the message from the messages table. This signals successful processing and triggers the mark_event_log_item_as_processed function to update the
         corresponding record in the event_log.

- Failed Processing:
  - Endpoint: POST /queues/messages/{messageId}/failed
  - Action: Updates the message's status to FAILED. These messages are not retried by the standard polling logic.
