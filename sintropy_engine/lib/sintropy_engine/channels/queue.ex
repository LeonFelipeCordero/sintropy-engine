defmodule SintropyEngine.Channel.Queue do
  use Ecto.Schema
  import Ecto.Changeset

  alias SintropyEngine.Channels.Channel

  @primary_key false
  @foreign_key_type :binary_id
  schema "queues" do
    field :consumption_type, Ecto.Enum, values: [:STANDARD, :FIFO]

    belongs_to :channel, Channel, primary_key: true

    timestamps(type: :utc_datetime)
  end

  @doc false
  def changeset(queue, attrs) do
    queue
    |> cast(attrs, [:consumption_type])
    |> validate_required([:consumption_type])
  end
end
