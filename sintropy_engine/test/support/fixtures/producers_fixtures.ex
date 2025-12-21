defmodule SintropyEngine.ProducersFixtures do
  @moduledoc """
  This module defines test helpers for creating
  entities via the `SintropyEngine.Producers` context.
  """

  import SintropyEngine.ChannelsFixtures

  @doc """
  Generate a producer.
  """
  def producer_fixture(attrs \\ %{}, consumption_type \\ :STANDARD) do
    channel =
      case consumption_type do
        :STANDARD -> channel_standard_queue()
        :FIFO -> channel_fifo_queue()
      end

    {:ok, producer} =
      attrs
      |> Enum.into(%{
        name: "some_name",
        channel_id: channel.id
      })
      |> SintropyEngine.Producers.create_producer()

    %{channel: channel, producer: producer}
  end

  def producer_without_channel_fixture(channel_id, attrs \\ %{}) do
    {:ok, producer} =
      attrs
      |> Enum.into(%{
        name: "some_name",
        channel_id: channel_id
      })
      |> SintropyEngine.Producers.create_producer()

    producer
  end

  def producer_without_existing_channel_fixture(attrs \\ %{}) do
    attrs
    |> Enum.into(%{
      name: "some_name",
      channel_id: Ecto.UUID.generate()
    })
    |> SintropyEngine.Producers.create_producer()
  end
end
