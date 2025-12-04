defmodule SintropyEngine.Repo.Migrations.CreateMessages do
  use Ecto.Migration

  def change do
    create table(:messages, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :timestamp, :utc_datetime
      add :routing_key, :string
      add :mesage, :text
      add :headers, :text
      add :status, :string
      add :last_delivered, :utc_datetime
      add :delivered_times, :integer

      add :channel_id, references(:channels, on_delete: :nothing, type: :binary_id)
      add :producer_id, references(:producers, on_delete: :nothing, type: :binary_id)

      timestamps(type: :utc_datetime)
    end

    create table(:events_log, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :timestamp, :utc_datetime
      add :routing_key, :string
      add :mesage, :text
      add :headers, :text
      add :processed, :boolean

      add :channel_id, references(:channels, on_delete: :nothing, type: :binary_id)
      add :producer_id, references(:producers, on_delete: :nothing, type: :binary_id)

      timestamps(type: :utc_datetime)
    end

    create index(:messages, [:channel_id])
    create index(:messages, [:producer_id])

    create index(:messages, [
             :channel_id,
             :routing_key,
             :status,
             :last_delivered,
             :delivered_times
           ])
  end
end
