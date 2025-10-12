--liquibase formatted sql

--changeset LeonFelipeCordero:002-create-channels-table logicalFilePath:db/changelog/001-create-channels-table.sql

create table channels
(
    channel_id uuid         not null primary key,
    name       varchar(256) not null,

    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

create unique index channels_name_idx on channels(name);

create table routing_key
(
    routing_key varchar(128) not null,
    channel_id  uuid         not null references channels (channel_id),
    primary key (routing_key, channel_id)
);
