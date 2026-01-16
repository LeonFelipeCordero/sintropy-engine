-- Channel Circuit Breaker for FIFO queues and streams
-- When a message fails on a FIFO channel, the circuit opens and all remaining messages
-- for that channel+routing_key are routed to the dead letter queue

CREATE TYPE circuit_state AS ENUM ('CLOSED', 'OPEN');
CREATE CAST (VARCHAR AS circuit_state) WITH INOUT AS IMPLICIT;

CREATE TABLE channel_circuit_breakers
(
    circuit_id        UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    channel_id        UUID          NOT NULL REFERENCES channels (channel_id) ON DELETE CASCADE,
    routing_key       VARCHAR(128)  NOT NULL,
    state             circuit_state NOT NULL DEFAULT 'CLOSED',
    opened_at         TIMESTAMPTZ,
    failed_message_id UUID,

    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    UNIQUE (channel_id, routing_key)
);

CREATE INDEX idx_circuit_breakers_channel_routing ON channel_circuit_breakers (channel_id, routing_key);
CREATE INDEX idx_circuit_breakers_state ON channel_circuit_breakers (state);

-- Auto-create circuit breaker when routing key is added
CREATE OR REPLACE FUNCTION create_circuit_breaker_for_routing_key()
    RETURNS trigger AS
$$
BEGIN
    INSERT INTO channel_circuit_breakers (channel_id, routing_key, state)
    VALUES (NEW.channel_id, NEW.routing_key, 'CLOSED')
    ON CONFLICT (channel_id, routing_key) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER create_circuit_breaker_on_routing_key_insert
    AFTER INSERT
    ON routing_keys
    FOR EACH ROW
EXECUTE FUNCTION create_circuit_breaker_for_routing_key();

-- Auto-delete circuit breaker when routing key is removed
CREATE OR REPLACE FUNCTION delete_circuit_breaker_for_routing_key()
    RETURNS trigger AS
$$
BEGIN
    DELETE FROM channel_circuit_breakers
    WHERE channel_id = OLD.channel_id
      AND routing_key = OLD.routing_key;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER delete_circuit_breaker_on_routing_key_delete
    AFTER DELETE
    ON routing_keys
    FOR EACH ROW
EXECUTE FUNCTION delete_circuit_breaker_for_routing_key();

-- Open circuit and move remaining messages to DLQ when a FAILED message is deleted
CREATE OR REPLACE FUNCTION open_circuit_on_failed_message_delete()
    RETURNS trigger AS
$$
DECLARE
    v_is_fifo BOOLEAN;
BEGIN
    -- Only process if the deleted message had FAILED status
    IF OLD.status != 'FAILED' THEN
        RETURN OLD;
    END IF;

    -- Check if channel is FIFO (stream or queue with FIFO consumption)
    SELECT EXISTS(
        SELECT 1
        FROM channels c
        LEFT JOIN queues q ON q.channel_id = c.channel_id
        WHERE c.channel_id = OLD.channel_id
          AND (c.channel_type = 'STREAM' OR q.consumption_type = 'FIFO')
    ) INTO v_is_fifo;

    IF NOT v_is_fifo THEN
        RETURN OLD;
    END IF;

    -- Open the circuit breaker
    UPDATE channel_circuit_breakers
    SET state             = 'OPEN',
        opened_at         = NOW(),
        failed_message_id = OLD.message_id,
        updated_at        = NOW()
    WHERE channel_id = OLD.channel_id
      AND routing_key = OLD.routing_key;

    -- Move all remaining messages to DLQ in a single statement
    WITH messages_to_move AS (
        DELETE FROM messages
        WHERE channel_id = OLD.channel_id
          AND routing_key = OLD.routing_key
          AND status IN ('READY', 'IN_FLIGHT')
        RETURNING *
    )
    INSERT INTO dead_letter_queue(message_id,
                                  timestamp,
                                  channel_id,
                                  producer_id,
                                  routing_key,
                                  message,
                                  headers,
                                  origin_message_id,
                                  delivered_times,
                                  failed_at)
    SELECT message_id,
           timestamp,
           channel_id,
           producer_id,
           routing_key,
           message,
           headers,
           origin_message_id,
           delivered_times,
           NOW()
    FROM messages_to_move;

    -- Clean up message_log for moved messages
    DELETE FROM message_log
    WHERE message_id IN (
        SELECT message_id
        FROM dead_letter_queue
        WHERE channel_id = OLD.channel_id
          AND routing_key = OLD.routing_key
          AND message_id != OLD.message_id
    );

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER open_circuit_on_failed_delete
    BEFORE DELETE
    ON messages
    FOR EACH ROW
EXECUTE FUNCTION open_circuit_on_failed_message_delete();
