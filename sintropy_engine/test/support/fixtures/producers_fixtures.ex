defmodule SintropyEngine.ProducersFixtures do
  @moduledoc """
  This module defines test helpers for creating
  entities via the `SintropyEngine.Producers` context.
  """

  import SintropyEngine.ChannelsFixtures

  @doc """
  Generate a producer.
  """
  def producer_fixture(attrs \\ %{}) do
    channel = channel_standard_queue()

    {:ok, producer} =
      attrs
      |> Enum.into(%{
        name: "some_name",
        channel_id: channel.id
      })
      |> SintropyEngine.Producers.create_producer()

    %{channel: channel, producer: producer}
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
