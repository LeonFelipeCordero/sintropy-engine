# NOTES

- compare validations with quarkus version
- solve associations between channel message routing key
- Add the option to add new routing keys to channels
- Add extra migration steps to compleate the message and even log

## Analysis from AI on requirements

1. Channel (`lib/sintropy_engine/channels/channel.ex`)

  When creating or updating a channel, the following validations are applied:

- `name`:
  - Required: A name must be provided.
  - Format: Cannot contain any whitespace characters.
- `channel_type`:
  - Required: A type must be specified.
  - Enum: Must be one of QUEUE or STREAM.
- Custom Logic:
  - If channel_type is QUEUE, an associated queue configuration is required.
  - If channel_type is STREAM, an associated queue configuration is not allowed.
- `routing_keys`:
  - Association: At least one routing key must be provided and associated with the channel.

  2. Queue (`lib/sintropy_engine/channels/queue.ex`)

  This is part of a channel's configuration when its type is QUEUE.

- `consumption_type`:
  - Required: A consumption type must be specified.
  - Enum: Must be one of STANDARD or FIFO.

  3. Routing Key (`lib/sintropy_engine/channels/routing_key.ex`)

- `routing_key`:
  - Required: The key itself is a required field.
  - Format: Cannot contain any whitespace characters.
- `channel`:
  - Association: It must be associated with a parent channel.

  4. Producer (`lib/sintropy_engine/producers/producer.ex`)

- `name`:
  - Required: A name must be provided.
  - Format: Cannot contain any whitespace characters.
- `channel_id`:
  - Required: A channel ID must be provided.
  - Foreign Key: The ID must reference a valid, existing channel.

  5. Message (`lib/sintropy_engine/messages/message.ex`)

- Required Fields: The following fields are all mandatory:
  - timestamp
  - routing_key
  - mesage (message content)
  - headers
  - status
  - last_delivered
  - delivered_times
  - channel_id
  - producer_id
- `status`:
  - Enum: Must be one of READY, IN_FLIGHT, or FAILED.
- Foreign Key Constraints:
  - channel_id must reference a valid, existing channel.
  - producer_id must reference a valid, existing producer.
- Custom `routing_key` Validation:
  - The provided routing_key is checked to ensure it has been registered for the given channel_id. If it does not exist for that channel, the message is considered invalid.

  When migrating to another language, you will need to implement these checks using your new framework's validation libraries to ensure data integrity is maintained.
