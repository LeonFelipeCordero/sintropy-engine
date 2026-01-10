defmodule SintropyEngineWeb.StreamChannel do
  @moduledoc """
  Phoenix Channel for streaming messages from STREAM channels via websocket.
  """
  use Phoenix.Channel

  alias SintropyEngine.Channels
  alias SintropyEngine.PubSub

  def join("stream:channel:" <> channel_id, _params, socket) do
    case Ecto.UUID.cast(channel_id) do
      {:ok, uuid} ->
        case Channels.get_channel!(uuid) do
          %{channel_type: :STREAM} ->
            topic = "stream:channel:#{channel_id}"
            Phoenix.PubSub.subscribe(PubSub, topic)
            {:ok, assign(socket, :channel_id, channel_id)}

          _ ->
            {:error, %{reason: "Channel is not a STREAM channel"}}
        end

      :error ->
        {:error, %{reason: "Invalid channel ID"}}
    end
  rescue
    Ecto.NoResultsError ->
      {:error, %{reason: "Channel not found"}}
  end

  def handle_info({:new_message, message}, socket) do
    push(socket, "new_message", message)
    {:noreply, socket}
  end

  def handle_info(_msg, socket) do
    {:noreply, socket}
  end
end
