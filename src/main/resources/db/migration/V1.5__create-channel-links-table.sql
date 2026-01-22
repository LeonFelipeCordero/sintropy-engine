CREATE TABLE channel_links (
    channel_link_id UUID PRIMARY KEY DEFAULT uuidv7(),
    source_channel_id UUID NOT NULL REFERENCES channels(channel_id) ON DELETE CASCADE,
    target_channel_id UUID NOT NULL REFERENCES channels(channel_id) ON DELETE CASCADE,
    source_routing_key VARCHAR(255) NOT NULL,
    target_routing_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    enabled BOOLEAN DEFAULT true,
    UNIQUE(source_channel_id, target_channel_id, source_routing_key, target_routing_key)
);

CREATE INDEX idx_channel_links_source ON channel_links(source_channel_id, source_routing_key);
CREATE INDEX idx_channel_links_target ON channel_links(target_channel_id);
