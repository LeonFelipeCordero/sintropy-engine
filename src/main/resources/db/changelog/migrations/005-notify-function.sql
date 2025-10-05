create or replace function notify_new_message() returns trigger as
$$
begin
    perform pg_notify('new_message',
                      json_build_object(
                              'channel_id', new.channel_id,
                              'routing_key', new.routing_key,
                              'message_id', new.message_id
                      )::text);
    return new;
end;
$$ language plpgsql;

create trigger messages_notify_trigger
    after insert
    on messages
    for each row
execute function notify_new_message();