defmodule SintropyEngine.Channels.Channel do
  use Ecto.Schema
  import Ecto.Changeset

  alias SintropyEngine.Channels.RoutingKey
  alias SintropyEngine.Channels.Queue

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "channels" do
    field :name, :string
    field :channel_type, Ecto.Enum, values: [:QUEUE, :STREAM]

    has_many :routing_keys, RoutingKey
    has_one :queue, Queue

    timestamps(type: :utc_datetime)
  end

  @doc false
  def changeset(channel, attrs) do
    channel
    |> cast(attrs, [:name, :channel_type])
    |> cast_assoc(:routing_keys, required: true)
    |> cast_assoc(:queue)
    |> validate_required([:name, :channel_type])
    |> validate_format(
      :name,
      # Matches a string with zero or more non-whitespace characters
      ~r/^\S*$/,
      message: "name can not contain any blank spaces"
    )
    |> validate_channel()
  end

  defp validate_channel(%Ecto.Changeset{} = changeset) do
    queue = get_field(changeset, :queue)

    case get_field(changeset, :channel_type) do
      :QUEUE ->
        if queue == nil do
          add_error(
            changeset,
            :channel_type,
            "Channel type QUEUE require one consumption type [STRANDARD, FIFO]"
          )
        else
          changeset
        end

      :STREAM ->
        if queue != nil do
          add_error(changeset, :channel_type, "Channel type STREAM can not have consumption type")
        else
          changeset
        end

      _ ->
        changeset
    end
  end
end
