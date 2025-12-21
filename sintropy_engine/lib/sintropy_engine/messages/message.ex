defmodule SintropyEngine.Messages.Message do
  use Ecto.Schema
  import Ecto.Changeset

  alias SintropyEngine.Channels
  alias SintropyEngine.Channels.Channel
  alias SintropyEngine.Producers.Producer

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "messages" do
    field :timestamp, :utc_datetime
    field :routing_key, :string
    field :message, :string
    field :headers, :string
    field :status, Ecto.Enum, values: [:READY, :IN_FLIGHT, :FAILED]
    field :last_delivered, :utc_datetime
    field :delivered_times, :integer

    belongs_to :channel, Channel
    belongs_to :producer, Producer

    timestamps(type: :utc_datetime)
  end

  @doc false
  def changeset(message, attrs) do
    message
    |> cast(attrs, [
      :timestamp,
      :routing_key,
      :message,
      :headers,
      :status,
      :last_delivered,
      :delivered_times,
      :channel_id,
      :producer_id
    ])
    |> validate_required([
      :timestamp,
      :routing_key,
      :message,
      :headers,
      :status,
      :delivered_times,
      :channel_id,
      :producer_id
    ])
    |> foreign_key_constraint(:channel_id, message: "Message needs a channel")
    |> foreign_key_constraint(:producer_id, message: "Message needs a producer")
    |> validate_routing_key()
  end

  defp validate_routing_key(changeset) do
    routing_key = get_field(changeset, :routing_key)
    channel_id = get_field(changeset, :channel_id)

    if routing_key && channel_id && is_nil(Channels.get_routing_key(channel_id, routing_key)) do
      add_error(changeset, :routing_key, "Routing key does not exist for this channel")
    else
      changeset
    end
  end
end
