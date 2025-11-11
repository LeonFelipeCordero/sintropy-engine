defmodule SintropyEngine.Repo.Migrations.CreateChannels do
  use Ecto.Migration

  def change do
    create table(:channels, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :name, :string
      add :channel_type, :string

      timestamps(type: :utc_datetime)
    end

    create table(:routing_keys, primary_key: false) do
      add :routing_key, :string, primary_key: true

      add(
        :channel_id,
        references(:channels, on_delete: :delete_all, type: :binary_id),
        primary_key: true
      )

      timestamps(type: :utc_datetime)
    end

    create table(:queues, primary_key: false) do
      add :consumption_type, :string

      add(
        :channel_id,
        references(:channels, on_delete: :delete_all, type: :binary_id),
        primary_key: true
      )

      timestamps(type: :utc_datetime)
    end

    create index(:channels, [:name])
    create index(:routing_keys, [:channel_id, :routing_key])
  end
end
