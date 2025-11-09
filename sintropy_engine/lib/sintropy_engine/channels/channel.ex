defmodule SintropyEngine.Channels.Channel do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "channels" do
    field :channel_id, Ecto.UUID
    field :name, :string
    field :channel_type, Ecto.Enum, values: [:QUEUE, :STREAM]

    timestamps(type: :utc_datetime)
  end

  @doc false
  def changeset(channel, attrs) do
    channel
    |> cast(attrs, [:channel_id, :name, :channel_type])
    |> validate_required([:channel_id, :name, :channel_type])
  end
end
