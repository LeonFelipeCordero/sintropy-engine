create table producers
(
    producer_id   bigserial    not null primary key,
    producer_uuid uuid         not null default gen_random_uuid() unique,
    name          varchar(128) not null,
    channel_id    bigint       not null references channels (channel_id),

    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);

create unique index producers_name_idx on producers (name);
create index producers_channel_id_idx on producers (channel_id);