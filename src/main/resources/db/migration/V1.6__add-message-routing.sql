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
            INSERT INTO messages (origin_message_id,
                                  timestamp,
                                  channel_id,
                                  producer_id,
                                  routing_key,
                                  message,
                                  headers,
                                  status,
                                  created_at,
                                  updated_at)
            VALUES (NEW.message_id,
                    NEW.timestamp,
                    link.target_channel_id,
                    NEW.producer_id,
                    link.target_routing_key,
                    NEW.message,
                    NEW.headers,
                    'READY',
                    NOW(),
                    NOW());
        END LOOP;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER route_message_after_insert
    AFTER INSERT
    ON messages
    FOR EACH ROW
EXECUTE FUNCTION route_message_to_linked_channels();
