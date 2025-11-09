defmodule SintropyEngine.Repo.Migrations.CreateChannels do
  use Ecto.Migration

  def change do
    create table(:channels, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :name, :string
      add :channel_type, :string

      timestamps(type: :utc_datetime)
    end
  end
end
