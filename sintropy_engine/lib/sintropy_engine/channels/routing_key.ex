defmodule SintropyEngine.Channels.RoutingKey do
  use Ecto.Schema
  import Ecto.Changeset

  alias SintropyEngine.Channels.Channel

  @primary_key false
  @foreign_key_type :binary_id
  schema "routing_keys" do
    field :routing_key, :string, primary_key: true

    belongs_to :channel, Channel, primary_key: true

    timestamps(type: :utc_datetime)
  end

  @doc false
  def changeset(routing_key, attrs) do
    routing_key
    |> cast(attrs, [:routing_key])
    |> validate_required([:routing_key])
    |> validate_format(
      :routing_key,
      # Matches a string with zero or more non-whitespace characters
      ~r/^\S*$/,
      message: "routing_key can not contain any blank spaces"
    )
  end
end
