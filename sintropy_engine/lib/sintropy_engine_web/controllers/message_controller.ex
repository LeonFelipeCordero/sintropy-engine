defmodule SintropyEngineWeb.MessageController do
  use SintropyEngineWeb, :controller

  alias SintropyEngine.Messages
  alias SintropyEngine.Channels
  alias SintropyEngine.Messages.Message
  alias Ecto.UUID

  action_fallback SintropyEngineWeb.FallbackController

  def index(conn, _params) do
    messages = Messages.list_messages()
    render(conn, :index, messages: messages)
  end

  def create(conn, %{"message" => message_params}) do
    with {:ok, %Message{} = message} <- Messages.create_message(message_params) do
      conn
      |> put_status(:created)
      |> put_resp_header("location", ~p"/api/messages/#{message}")
      |> render(:show, message: message)
    end
  end

  def show(conn, %{"id" => id}) do
    message = Messages.get_message!(id)
    render(conn, :show, message: message)
  end

  def update(conn, %{"id" => id, "message" => message_params}) do
    message = Messages.get_message!(id)

    with {:ok, %Message{} = message} <- Messages.update_message(message, message_params) do
      render(conn, :show, message: message)
    end
  end

  def delete(conn, %{"id" => id}) do
    message = Messages.get_message!(id)

    with {:ok, %Message{}} <- Messages.delete_message(message) do
      send_resp(conn, :no_content, "")
    end
  end

  def poll(conn, %{"channel_id" => channel_id, "routing_key" => routing_key} = params) do
    polling_count = Map.get(params, "polling_count", 1)

    channel = Channels.get_channel!(channel_id)

    if channel.channel_type != :QUEUE do
      conn
      |> put_status(:bad_request)
      |> json(%{error: "Polling is only available for QUEUE channels"})
    else
      channel_uuid = UUID.dump!(channel.id)

      messages =
        case channel.queue.consumption_type do
          :STANDARD -> Messages.poll_standard(channel_uuid, routing_key, polling_count)
          :FIFO -> Messages.poll_fifo(channel_uuid, routing_key, polling_count)
          _ -> []
        end

      render(conn, :index, messages: messages)
    end
  end
end
