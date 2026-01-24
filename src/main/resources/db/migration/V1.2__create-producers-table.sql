create table producers
(
    producer_id uuid         not null default uuidv7() primary key,
    name        varchar(128) not null,

    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create unique index producers_name_idx on producers (name);
