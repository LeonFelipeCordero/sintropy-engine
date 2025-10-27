create type consumer_connection_type as enum ('POLLING', 'STREAMING');
create cast (varchar as consumer_connection_type) with inout as implicit;

create table consumers
(
    consumer_id     uuid                     not null primary key,
    channel_id      uuid                     not null references channels (channel_id),
    routing_key     varchar(128)             not null,
    connection_type consumer_connection_type not null,

    created_at      timestamptz              not null default now(),
    updated_at      timestamptz              not null default now()
);

create index consumers_channel_id_idx on consumers (channel_id);
create index consumerS_routing_key on consumers (routing_key);