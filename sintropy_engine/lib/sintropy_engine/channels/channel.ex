defmodule SintropyEngine.Channels.Channel do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "channels" do
    field :name, :string
    field :channel_type, Ecto.Enum, values: [:QUEUE, :STREAM]

    timestamps(type: :utc_datetime)
  end

  @doc false
  def changeset(channel, attrs) do
    channel
    |> cast(attrs, [:name, :channel_type])
    |> validate_required([:name, :channel_type])
    |> validate_format(
      :name,
      # Matches a string with zero or more non-whitespace characters
      ~r/^\S*$/,
      message: "name can not contain any blank spaces"
    )
  end
end
