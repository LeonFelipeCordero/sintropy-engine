defmodule SintropyEngineWeb.MessageJSON do
  alias SintropyEngine.Messages.Message

  @doc """
  Renders a list of messages.
  """
  def index(%{messages: messages}) do
    %{data: for(message <- messages, do: data(message))}
  end

  @doc """
  Renders a single message.
  """
  def show(%{message: message}) do
    %{data: data(message)}
  end

  defp data(%Message{} = message) do
    %{
      id: message.id,
      timestamp: message.timestamp,
      routing_key: message.routing_key,
      mesage: message.mesage,
      headers: message.headers,
      status: message.status,
      last_delivered: message.last_delivered,
      delivered_times: message.delivered_times
    }
  end
end
