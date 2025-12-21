defmodule SintropyEngine.Messages.EventLog do
  use Ecto.Schema
  import Ecto.Changeset

  alias SintropyEngine.Channels.Channel
  alias SintropyEngine.Producers.Producer

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "event_log" do
    field :timestamp, :utc_datetime
    field :routing_key, :string
    field :message, :string
    field :headers, :string
    field :processed, :boolean, default: false

    belongs_to :channel, Channel
    belongs_to :producer, Producer

    timestamps(type: :utc_datetime)
  end

  @doc false
  def changeset(event_log, attrs) do
    event_log
    |> cast(attrs, [
      :message_id,
      :timestamp,
      :routing_key,
      :message,
      :headers,
      :processed,
      :channel_id,
      :producer_id
    ])
    |> validate_required([
      :message_id,
      :timestamp,
      :routing_key,
      :message,
      :headers,
      :processed,
      :channel_id,
      :producer_id
    ])
    |> foreign_key_constraint(:channel_id)
    |> foreign_key_constraint(:producer_id)
  end
end
