--liquibase formatted sql

--changeset LeonFelipeCordero:004-create-messages_table logicalFilePath:db/changelog/004-create-messages-table.sql splitStatements:false

create type message_status_type as enum ('READY', 'IN_FLIGHT', 'FAILED');
create cast (varchar as message_status_type) with inout as implicit;

create table messages
(
    message_id      uuid                not null,
    timestamp       timestamptz         not null,
    channel_id      uuid                not null references channels (channel_id),
    producer_id     uuid                not null references producers (producer_id),
    routing_key     varchar(128)        not null,
    message         text                not null,
    status          message_status_type not null default 'READY',
    last_delivered  timestamptz,
    delivered_times int4                not null default 0,

    created_at      timestamptz         not null default now(),
    updated_at      timestamptz         not null default now(),

    constraint messages_message_id_pk primary key (message_id)
);
-- TODO things to consider to test, 1) is index by delivered times useful?, 2) is last delivered useful?
create index messages_polling_1idx
    on messages (channel_id, routing_key, status, last_delivered, delivered_times);

-- todo in case the index above is not good, try by creating others more granular
-- create unique index messages_channel_id_routing_key_idx
--     on messages (channel_id, routing_key);
-- create unique index messages_channel_id_routing_key_ready_idx
--     on messages (channel_id, routing_key) where status = 'READY';
-- todo let's try this one when metrics will be needed
-- create index messages_status_idx on messages (status);

create table event_log
(
    message_id  uuid         not null,
    timestamp   timestamptz  not null,
    channel_id  uuid         not null references channels (channel_id),
    producer_id uuid         not null references producers (producer_id),
    routing_key varchar(128) not null,
    message     text         not null, -- todo JSONB maybe?
    processed   bool         not null default false,

    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,

    constraint event_log_message_id_timestamp_pk primary key (message_id, timestamp, channel_id)
);

create or replace function messages_to_event_log()
    returns trigger as
$$
begin
    insert into event_log(message_id,
                          timestamp,
                          channel_id,
                          producer_id,
                          routing_key,
                          message,
                          processed,
                          created_at,
                          updated_at)
    values (new.message_id,
            new.timestamp,
            new.channel_id,
            new.producer_id,
            new.routing_key,
            new.message,
            false,
            new.created_at,
            new.updated_at);

    return new;
end;
$$ language plpgsql;

create trigger insert_into_event_log
    after insert
    on messages
    for each row
execute function messages_to_event_log();


create or replace function mark_event_log_item_as_processed()
    returns trigger as
$$
begin
    update event_log
    set processed  = true,
        updated_at = now()
    where message_id = old.message_id;

    return old;
end;
$$ language plpgsql;

create trigger mark_as_deliver_in_event_log
    after delete
    on messages
    for each row
execute function mark_event_log_item_as_processed();

