--liquibase formatted sql

--changeset LeonFelipeCordero:003-create-producers-table logicalFilePath:db/changelog/003-create-producers-table.sql

create table producers
(
    producer_id uuid         not null primary key,
    name        varchar(128) not null,
    channel_id  uuid         not null references channels (channel_id),

    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create unique index producers_name_idx on producers (name);
create index producers_channel_id_idx on producers (channel_id);