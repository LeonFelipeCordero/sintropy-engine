defmodule SintropyEngine.E2eFixtures do
  use SintropyEngineWeb.ConnCase

  def create_standard_channel(conn) do
    standard_channel_attrs = %{
      name: "standard_test_channel",
      channel_type: "QUEUE",
      routing_keys: [%{routing_key: "test.standard"}],
      queue: %{consumption_type: "STANDARD"}
    }

    conn = post(conn, ~p"/api/channels", channel: standard_channel_attrs)
    %{"id" => channel_id} = json_response(conn, 201)["data"]

    channel_id
  end

  def create_fifo_channel(conn) do
    fifo_channel_attrs = %{
      name: "fifo_test_channel",
      channel_type: "QUEUE",
      routing_keys: [%{routing_key: "test.fifo"}],
      queue: %{consumption_type: "FIFO"}
    }

    conn = post(conn, ~p"/api/channels", channel: fifo_channel_attrs)
    %{"id" => channel_id} = json_response(conn, 201)["data"]

    channel_id
  end

  def create_create_producer(conn, channel_id) do
    conn =
      post(conn, ~p"/api/producers", producer: %{name: "test_producer", channel_id: channel_id})

    %{"id" => producer_id} = json_response(conn, 201)["data"]

    producer_id
  end

  def send_message(conn, attrs) do
    conn = post(conn, ~p"/api/messages", message: attrs)
    %{"id" => message_id} = json_response(conn, 201)["data"]

    message_id
  end

  def poll_messages(conn, attrs) do
    conn = post(conn, ~p"/api/messages/poll", attrs)
    polled_messages = json_response(conn, 200)["data"]
    polled_messages
  end

  def dequeue_message(conn, message_id) do
    conn = delete(conn, ~p"/api/messages/#{message_id}")
    response(conn, 204)
  end
end
