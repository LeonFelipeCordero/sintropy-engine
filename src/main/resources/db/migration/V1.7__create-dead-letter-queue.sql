-- Dead Letter Queue table
CREATE TABLE dead_letter_queue
(
    dlq_entry_id      UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    message_id        UUID         NOT NULL DEFAULT gen_random_uuid(),
    timestamp         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    channel_id        UUID         NOT NULL REFERENCES channels (channel_id),
    producer_id       UUID         NOT NULL REFERENCES producers (producer_id),
    routing_key       VARCHAR(128) NOT NULL,
    message           JSONB        NOT NULL,
    headers           JSONB        NOT NULL,
    origin_message_id UUID,
    delivered_times   INT4         NOT NULL,
    failed_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX dlq_channel_routing_idx ON dead_letter_queue (channel_id, routing_key);
CREATE INDEX dlq_message_id_idx ON dead_letter_queue (message_id);
CREATE INDEX dlq_failed_at_idx ON dead_letter_queue (failed_at);


-- Function to trigger DELETE on FAILED status update
CREATE OR REPLACE FUNCTION route_message_to_dql()
    RETURNS trigger AS
$$
BEGIN
    IF NEW.status = 'FAILED' AND OLD.status != 'FAILED' THEN
        -- Create a copy that is marked as failed in the DQL
        INSERT INTO dead_letter_queue(message_id,
                                      origin_message_id,
                                      timestamp,
                                      channel_id,
                                      producer_id,
                                      routing_key,
                                      message,
                                      headers,
                                      delivered_times,
                                      failed_at)
        VALUES (OLD.message_id,
                OLD.origin_message_id,
                OLD.timestamp,
                OLD.channel_id,
                OLD.producer_id,
                OLD.routing_key,
                OLD.message,
                OLD.headers,
                OLD.delivered_times,
                NOW());

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
EXECUTE FUNCTION route_message_to_dql();
