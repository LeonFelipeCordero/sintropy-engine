defmodule SintropyEngine.ChannelsFixtures do
  @moduledoc """
  This module defines test helpers for creating
  entities via the `SintropyEngine.Channels` context.
  """

  @doc """
  Generate a channel.
  """
  def channel_fixture(attrs \\ %{}) do
    {:ok, channel} =
      attrs
      |> Enum.into(%{
        channel_type: :QUEUE,
        name: "some_name"
      })
      |> SintropyEngine.Channels.create_channel()

    channel
  end
end
