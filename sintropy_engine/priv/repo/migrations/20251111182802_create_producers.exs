defmodule SintropyEngine.Repo.Migrations.CreateProducers do
  use Ecto.Migration

  def change do
    create table(:producers, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :name, :string

      add :channel_id, references(:channels, on_delete: :delete_all, type: :binary_id)

      timestamps(type: :utc_datetime)
    end

    create index(:producers, [:channel_id])
    create index(:producers, [:name])
  end
end
