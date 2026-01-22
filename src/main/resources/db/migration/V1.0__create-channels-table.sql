create type channel_type as enum ('QUEUE', 'STREAM');
create cast (varchar as channel_type) with inout as implicit;

create table channels
(
    channel_id   bigserial    not null primary key,
    channel_uuid uuid         not null default gen_random_uuid() unique,
    name         varchar(256) not null,
    channel_type channel_type not null,

    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now()
);

create unique index channels_name_idx on channels (name);

create table routing_keys
(
    routing_key varchar(128) not null,
    channel_id  bigint       not null references channels (channel_id),
    unique (routing_key, channel_id)
);

create type consumption_type as enum ('STANDARD', 'FIFO');
create cast (varchar as consumption_type) with inout as implicit;

create table queues
(
    channel_id       bigint           not null references channels (channel_id) unique,
    consumption_type consumption_type not null
);
