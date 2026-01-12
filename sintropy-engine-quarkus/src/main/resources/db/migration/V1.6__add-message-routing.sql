ALTER TABLE messages ADD COLUMN origin_message_id UUID REFERENCES messages(message_id) ON DELETE SET NULL;
CREATE INDEX messages_origin_idx ON messages(origin_message_id);

ALTER TABLE message_log ADD COLUMN origin_message_id UUID;

CREATE OR REPLACE FUNCTION messages_to_message_log()
    RETURNS trigger AS
$$
BEGIN
    INSERT INTO message_log(message_id,
                            timestamp,
                            channel_id,
                            producer_id,
                            routing_key,
                            message,
                            headers,
                            processed,
                            origin_message_id,
                            created_at,
                            updated_at)
    VALUES (NEW.message_id,
            NEW.timestamp,
            NEW.channel_id,
            NEW.producer_id,
            NEW.routing_key,
            NEW.message,
            NEW.headers,
            false,
            NEW.origin_message_id,
            NEW.created_at,
            NEW.updated_at);

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION route_message_to_linked_channels()
    RETURNS trigger AS
$$
DECLARE
    link RECORD;
BEGIN
    IF NEW.origin_message_id IS NOT NULL THEN
        RETURN NEW;
    END IF;

    FOR link IN
        SELECT target_channel_id, target_routing_key
        FROM channel_links
        WHERE source_channel_id = NEW.channel_id
          AND source_routing_key = NEW.routing_key
          AND enabled = true
    LOOP
        INSERT INTO messages (
            timestamp,
            channel_id,
            producer_id,
            routing_key,
            message,
            headers,
            status,
            origin_message_id,
            created_at,
            updated_at
        ) VALUES (
            NEW.timestamp,
            link.target_channel_id,
            NEW.producer_id,
            link.target_routing_key,
            NEW.message,
            NEW.headers,
            'READY',
            NEW.message_id,
            NOW(),
            NOW()
        );
    END LOOP;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER route_message_after_insert
    AFTER INSERT
    ON messages
    FOR EACH ROW
EXECUTE FUNCTION route_message_to_linked_channels();
