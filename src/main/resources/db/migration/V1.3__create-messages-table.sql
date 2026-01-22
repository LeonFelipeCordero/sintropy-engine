create type message_status_type as enum ('READY', 'IN_FLIGHT', 'FAILED');
create cast (varchar as message_status_type) with inout as implicit;

create table messages
(
    message_id        bigserial           not null primary key,
    message_uuid      uuid                not null default gen_random_uuid() unique,
    origin_message_id uuid,
    timestamp         timestamptz         not null default now(),
    channel_id        bigint              not null references channels (channel_id),
    producer_id       bigint              not null references producers (producer_id),
    routing_key       varchar(128)        not null,
    message           jsonb               not null,
    headers           jsonb               not null,
    status            message_status_type not null default 'READY',
    last_delivered    timestamptz,
    delivered_times   int4                not null default 0,

    created_at        timestamptz         not null default now(),
    updated_at        timestamptz         not null default now()
);

create index messages_polling_idx on messages (channel_id, routing_key, status, last_delivered, delivered_times);
create index messages_origin_message_idx on messages (origin_message_id);

create table message_log
(
    message_id        bigserial    not null primary key,
    message_uuid      uuid         not null,
    origin_message_id uuid,
    timestamp         timestamptz  not null,
    channel_id        bigint       not null references channels (channel_id),
    producer_id       bigint       not null references producers (producer_id),
    routing_key       varchar(128) not null,
    message           jsonb        not null,
    headers           jsonb        not null,
    processed         bool         not null default false,

    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,

    unique (message_uuid, timestamp, channel_id)
);

create or replace function messages_to_message_log()
    returns trigger as
$$
begin
    insert into message_log(message_id,
                            message_uuid,
                            origin_message_id,
                            timestamp,
                            channel_id,
                            producer_id,
                            routing_key,
                            message,
                            headers,
                            processed,
                            created_at,
                            updated_at)
    values (new.message_id,
            new.message_uuid,
            new.origin_message_id,
            new.timestamp,
            new.channel_id,
            new.producer_id,
            new.routing_key,
            new.message,
            new.headers,
            false,
            new.created_at,
            new.updated_at);

    return new;
end;
$$ language plpgsql;

create trigger insert_into_message_log
    after insert
    on messages
    for each row
execute function messages_to_message_log();


create or replace function mark_message_log_item_as_processed()
    returns trigger as
$$
begin
    -- Only mark as processed if the message was NOT failed
    -- Failed messages should keep processed = false in message_log
    -- Messages that are in status FAILED are deleted to don't affect performance in this table
    IF OLD.status != 'FAILED' THEN
        update message_log
        set processed  = true,
            updated_at = now()
        where message_id = old.message_id;
    END IF;

    return old;
end;
$$ language plpgsql;

create trigger mark_as_delivered_in_message_log
    after delete
    on messages
    for each row
execute function mark_message_log_item_as_processed();
