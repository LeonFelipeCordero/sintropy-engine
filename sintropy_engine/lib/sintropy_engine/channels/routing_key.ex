defmodule SintropyEngine.Channels.RoutingKey do
  use Ecto.Schema
  import Ecto.Changeset

  alias SintropyEngine.Channels.Channel
  alias SintropyEngine.Channels

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

  @doc false
  def full_changeset(routing_key, attrs) do
    routing_key
    |> cast(attrs, [:routing_key, :channel_id])
    |> validate_required([:routing_key, :channel_id])
    |> validate_format(
      :routing_key,
      # Matches a string with zero or more non-whitespace characters
      ~r/^\S*$/,
      message: "routing_key can not contain any blank spaces"
    )
    |> validate_channel()
  end

  defp validate_channel(changeset) do
    channel_id = get_field(changeset, :channel_id)

    if channel_id && is_nil(Channels.get_channel!(channel_id)) do
      add_error(changeset, :channel, "Channel does not exist #{channel_id}")
    else
      changeset
    end
  end
end
