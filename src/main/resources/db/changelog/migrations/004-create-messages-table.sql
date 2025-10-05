--liquibase formatted sql

--changeset LeonFelipeCordero:004-create-messages_table logicalFilePath:db/changelog/004-create-messages-table.sql

create table messages
(
    message_id  uuid         not null,
    timestamp   timestamptz  not null,
    channel_id  uuid         not null references channels (channel_id),
    producer_id uuid         not null references producers (producer_id),
    routing_key varchar(128) not null,
    message     text         not null, -- todo JSONB maybe?

    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),

    constraint messages_message_id_timestamp_ok primary key (message_id, timestamp, channel_id)
);

select create_hypertable('messages', by_range('timestamp'));
select *
from add_dimension('messages', by_hash('channel_id', 3));

create index messages_producer_id_idx on messages(producer_id);