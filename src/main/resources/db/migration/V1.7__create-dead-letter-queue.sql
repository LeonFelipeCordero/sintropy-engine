-- Dead Letter Queue table
CREATE TABLE dead_letter_queue (
    dlq_entry_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id        UUID NOT NULL,
    timestamp         TIMESTAMPTZ NOT NULL,
    channel_id        UUID NOT NULL REFERENCES channels(channel_id),
    producer_id       UUID NOT NULL REFERENCES producers(producer_id),
    routing_key       VARCHAR(128) NOT NULL,
    message           JSONB NOT NULL,
    headers           JSONB NOT NULL,
    origin_message_id UUID,
    delivered_times   INT4 NOT NULL,
    failed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dlq_channel_routing ON dead_letter_queue(channel_id, routing_key);
CREATE INDEX idx_dlq_message_id ON dead_letter_queue(message_id);
CREATE INDEX idx_dlq_failed_at ON dead_letter_queue(failed_at);

-- Function to move failed messages to DLQ (runs BEFORE DELETE)
CREATE OR REPLACE FUNCTION move_failed_message_to_dlq()
    RETURNS trigger AS
$$
BEGIN
    IF OLD.status = 'FAILED' THEN
        INSERT INTO dead_letter_queue(
            message_id,
            timestamp,
            channel_id,
            producer_id,
            routing_key,
            message,
            headers,
            origin_message_id,
            delivered_times,
            failed_at
        ) VALUES (
            OLD.message_id,
            OLD.timestamp,
            OLD.channel_id,
            OLD.producer_id,
            OLD.routing_key,
            OLD.message,
            OLD.headers,
            OLD.origin_message_id,
            OLD.delivered_times,
            NOW()
        );
    END IF;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- Trigger to move failed messages BEFORE delete (so we can check status)
CREATE TRIGGER move_to_dlq_before_delete
    BEFORE DELETE
    ON messages
    FOR EACH ROW
EXECUTE FUNCTION move_failed_message_to_dlq();

-- Update the mark_message_log_item_as_processed function
-- Only mark as processed if NOT failed
CREATE OR REPLACE FUNCTION mark_message_log_item_as_processed()
    RETURNS trigger AS
$$
BEGIN
    -- Only mark as processed if the message was NOT failed
    -- Failed messages should keep processed = false in message_log
    IF OLD.status != 'FAILED' THEN
        UPDATE message_log
        SET processed  = true,
            updated_at = now()
        WHERE message_id = OLD.message_id;
    END IF;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- Function to trigger DELETE on FAILED status update
CREATE OR REPLACE FUNCTION delete_failed_message()
    RETURNS trigger AS
$$
BEGIN
    IF NEW.status = 'FAILED' AND OLD.status != 'FAILED' THEN
        -- This DELETE will trigger:
        -- 1. move_to_dlq_before_delete (inserts to DLQ)
        -- 2. mark_as_delivered_in_message_log (keeps processed=false for FAILED)
        DELETE FROM messages WHERE message_id = NEW.message_id;
        DELETE FROM message_log WHERE message_id = NEW.message_id;
        RETURN NULL; -- Prevent the UPDATE from completing (row is deleted)
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER auto_delete_on_failed
    AFTER UPDATE OF status
    ON messages
    FOR EACH ROW
    WHEN (NEW.status = 'FAILED')
EXECUTE FUNCTION delete_failed_message();
