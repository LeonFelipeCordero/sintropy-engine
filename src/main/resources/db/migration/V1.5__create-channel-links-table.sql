CREATE TABLE channel_links
(
    channel_link_id    bigserial PRIMARY KEY,
    channel_link_uuid  UUID         NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    source_channel_id  BIGINT       NOT NULL REFERENCES channels (channel_id) ON DELETE CASCADE,
    target_channel_id  BIGINT       NOT NULL REFERENCES channels (channel_id) ON DELETE CASCADE,
    source_routing_key VARCHAR(255) NOT NULL,
    target_routing_key VARCHAR(255) NOT NULL,
    created_at         TIMESTAMPTZ           DEFAULT NOW(),
    updated_at         TIMESTAMPTZ           DEFAULT NOW(),
    enabled            BOOLEAN               DEFAULT true,
    UNIQUE (source_channel_id, target_channel_id, source_routing_key, target_routing_key)
);

CREATE INDEX channel_links_source_idx ON channel_links (source_channel_id, source_routing_key);
CREATE INDEX channel_links_target_idx ON channel_links (target_channel_id);
create index channel_links_channel_uuid_idx on channel_links (channel_link_uuid);
