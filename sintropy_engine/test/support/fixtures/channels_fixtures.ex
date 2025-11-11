defmodule SintropyEngine.ChannelsFixtures do
  @moduledoc """
  This module defines test helpers for creating
  entities via the `SintropyEngine.Channels` context.
  """

  alias SintropyEngine.Channels.Channel

  @doc """
  Generate a channel.
  """
  def channel_standard_queue(attrs \\ %{}) do
    {:ok, %Channel{} = channel} =
      attrs
      |> Enum.into(%{
        channel_type: :QUEUE,
        name: "some_name",
        routing_keys: [%{routing_key: "test.1"}],
        queue: %{consumption_type: :STANDARD}
      })
      |> SintropyEngine.Channels.create_channel()

    channel
  end

  def channel_fifo_queue(attrs \\ %{}) do
    {:ok, channel} =
      attrs
      |> Enum.into(%{
        channel_type: :QUEUE,
        name: "some_name",
        routing_keys: [%{routing_key: "test.1"}],
        queue: %{consumption_type: :FIFO}
      })
      |> SintropyEngine.Channels.create_channel()

    channel
  end

  def channel_stream(attrs \\ %{}) do
    {:ok, channel} =
      attrs
      |> Enum.into(%{
        channel_type: :STREAM,
        name: "some_name",
        routing_keys: [%{routing_key: "test.1"}]
      })
      |> SintropyEngine.Channels.create_channel()

    channel
  end
end
