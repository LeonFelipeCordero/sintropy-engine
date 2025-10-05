--liquibase formatted sql

--changeset LeonFelipeCordero:002-create-consumers-table logicalFilePath:db/changelog/002-create-consumers-table.sql

create table consumers
(
    consumer_id uuid         not null primary key,
    channel_id  uuid         not null references channels (channel_id),
    routing_key varchar(128) not null,

    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);


create index consumers_channel_id_idx on consumers (channel_id);
create index consumerS_routing_key on consumers (routing_key);