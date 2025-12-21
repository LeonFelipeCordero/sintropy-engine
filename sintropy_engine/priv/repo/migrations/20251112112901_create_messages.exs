defmodule SintropyEngine.Repo.Migrations.CreateMessages do
  use Ecto.Migration

  def up do
    create table(:messages, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :timestamp, :utc_datetime
      add :routing_key, :string
      add :message, :text
      add :headers, :text
      add :status, :string
      add :last_delivered, :utc_datetime
      add :delivered_times, :integer

      add :channel_id, references(:channels, on_delete: :nothing, type: :binary_id)
      add :producer_id, references(:producers, on_delete: :nothing, type: :binary_id)

      timestamps(type: :utc_datetime)
    end

    create table(:event_log, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :timestamp, :utc_datetime
      add :routing_key, :string
      add :message, :text
      add :headers, :text
      add :processed, :boolean

      add :channel_id, references(:channels, on_delete: :nothing, type: :binary_id)
      add :producer_id, references(:producers, on_delete: :nothing, type: :binary_id)

      timestamps(type: :utc_datetime)
    end

    create index(:messages, [:channel_id])
    create index(:messages, [:producer_id])

    create(
      index(:messages, [
        :channel_id,
        :routing_key,
        :status,
        :last_delivered,
        :delivered_times
      ])
    )

    execute("""
    CREATE OR REPLACE FUNCTION messages_to_event_log()
        RETURNS trigger AS
    $$
    BEGIN
        INSERT INTO event_log(id,
                              timestamp,
                              channel_id,
                              producer_id,
                              routing_key,
                              message,
                              headers,
                              processed,
                              inserted_at,
                              updated_at)
        VALUES (new.id,
                new.timestamp,
                new.channel_id,
                new.producer_id,
                new.routing_key,
                new.message,
                new.headers,
                false,
                new.inserted_at,
                new.updated_at);

        RETURN new;
    END;
    $$ LANGUAGE plpgsql;
    """)

    execute("""
    CREATE TRIGGER insert_into_event_log
        AFTER INSERT
        ON messages
        FOR EACH ROW
    EXECUTE FUNCTION messages_to_event_log();
    """)

    execute("""
    CREATE OR REPLACE FUNCTION mark_event_log_item_as_processed()
        RETURNS trigger AS
    $$
    BEGIN
        UPDATE event_log
        SET processed  = true,
            updated_at = now()
        WHERE id = old.id;

        RETURN old;
    END;
    $$ LANGUAGE plpgsql;
    """)

    execute("""
    CREATE TRIGGER mark_as_deliver_in_event_log
        AFTER DELETE
        ON messages
        FOR EACH ROW
    EXECUTE FUNCTION mark_event_log_item_as_processed();
    """)
  end
end
