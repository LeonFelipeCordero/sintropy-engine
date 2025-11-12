defmodule SintropyEngine.Producers.Producer do
  use Ecto.Schema
  import Ecto.Changeset

  alias SintropyEngine.Channels.Channel

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "producers" do
    field :name, :string

    belongs_to :channel, Channel

    timestamps(type: :utc_datetime)
  end

  @doc false
  def changeset(producer, attrs) do
    producer
    |> cast(attrs, [:name, :channel_id])
    |> validate_required([:name, :channel_id])
    |> validate_format(
      :name,
      # Matches a string with zero or more non-whitespace characters
      ~r/^\S*$/,
      message: "name can not contain any blank spaces"
    )
    |> foreign_key_constraint(:channel_id, message: "Producer needs a channel")
  end
end
