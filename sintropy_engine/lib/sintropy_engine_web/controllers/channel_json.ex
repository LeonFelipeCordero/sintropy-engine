defmodule SintropyEngineWeb.ChannelJSON do
  alias SintropyEngine.Channels.Channel

  @doc """
  Renders a list of channels.
  """
  def index(%{channels: channels}) do
    %{data: for(channel <- channels, do: data(channel))}
  end

  @doc """
  Renders a single channel.
  """
  def show(%{channel: channel}) do
    %{data: data(channel)}
  end

  defp data(%Channel{} = channel) do
    %{
      id: channel.id,
      channel_id: channel.channel_id,
      name: channel.name,
      channel_type: channel.channel_type
    }
  end
end
