defmodule SintropyEngine.SingleChannelStandarPollingE2eTest do
  use SintropyEngineWeb.ConnCase

  @standard_channel_attrs %{
    name: "standard_test_channel",
    channel_type: "QUEUE",
    routing_keys: [%{routing_key: "test.standard"}],
    queue: %{consumption_type: "STANDARD"}
  }

  setup %{conn: conn} do
    {:ok, conn: put_req_header(conn, "accept", "application/json")}
  end

  describe "E2E test for testing a standard channel" do
    test "full E2E flow: create channel, publish message, poll, and dequeue via API", %{
      conn: conn
    } do
      conn = post(conn, ~p"/api/channels", channel: @standard_channel_attrs)
      %{"id" => channel_id} = json_response(conn, 201)["data"]

      conn =
        post(conn, ~p"/api/producers", producer: %{name: "test_producer", channel_id: channel_id})

      %{"id" => producer_id} = json_response(conn, 201)["data"]

      message_attrs = %{
        channel_id: channel_id,
        producer_id: producer_id,
        routing_key: "test.standard",
        message: "Test message",
        headers: "Content-Type: text/plain",
        status: "READY",
        timestamp: DateTime.now!("Etc/UTC") |> DateTime.to_iso8601()
      }

      conn = post(conn, ~p"/api/messages", message: message_attrs)
      %{"id" => message_id} = json_response(conn, 201)["data"]

      conn =
        post(conn, ~p"/api/messages/poll", %{
          channel_id: channel_id,
          routing_key: "test.standard",
          polling_count: 1
        })

      polled_messages = json_response(conn, 200)["data"]
      assert length(polled_messages) == 1
      polled_message = List.first(polled_messages)
      assert polled_message["id"] == message_id
      assert polled_message["status"] == "IN_FLIGHT"

      conn =
        post(conn, ~p"/api/messages/poll", %{
          channel_id: channel_id,
          routing_key: "test.standard",
          polling_count: 1
        })

      polled_messages_again = json_response(conn, 200)["data"]
      assert polled_messages_again == []

      conn = delete(conn, ~p"/api/messages/#{message_id}")
      assert response(conn, 204)

      assert_error_sent 404, fn ->
        get(conn, ~p"/api/messages/#{message_id}")
      end
    end

    test "polling from STREAM channel returns error via API", %{conn: conn} do
      stream_channel_attrs = %{
        name: "stream_test_channel",
        channel_type: "STREAM",
        routing_keys: [%{routing_key: "test.stream"}]
      }

      conn = post(conn, ~p"/api/channels", channel: stream_channel_attrs)
      %{"id" => channel_id} = json_response(conn, 201)["data"]

      conn =
        post(conn, ~p"/api/messages/poll", %{
          channel_id: channel_id,
          routing_key: "test.stream",
          polling_count: 1
        })

      assert json_response(conn, 400)["error"] == "Polling is only available for QUEUE channels"
    end

    test "publish two messages and poll both in a single call", %{conn: conn} do
      conn = post(conn, ~p"/api/channels", channel: @standard_channel_attrs)
      %{"id" => channel_id} = json_response(conn, 201)["data"]

      conn =
        post(conn, ~p"/api/producers", producer: %{name: "test_producer", channel_id: channel_id})

      %{"id" => producer_id} = json_response(conn, 201)["data"]

      message_attrs_1 = %{
        channel_id: channel_id,
        producer_id: producer_id,
        routing_key: "test.standard",
        message: "First test message",
        headers: "Content-Type: text/plain",
        status: "READY",
        timestamp: DateTime.now!("Etc/UTC") |> DateTime.to_iso8601()
      }

      message_attrs_2 = %{
        channel_id: channel_id,
        producer_id: producer_id,
        routing_key: "test.standard",
        message: "Second test message",
        headers: "Content-Type: text/plain",
        status: "READY",
        timestamp: DateTime.now!("Etc/UTC") |> DateTime.to_iso8601()
      }

      conn = post(conn, ~p"/api/messages", message: message_attrs_1)
      %{"id" => message_id_1} = json_response(conn, 201)["data"]

      conn = post(conn, ~p"/api/messages", message: message_attrs_2)
      %{"id" => message_id_2} = json_response(conn, 201)["data"]

      conn =
        post(conn, ~p"/api/messages/poll", %{
          channel_id: channel_id,
          routing_key: "test.standard",
          polling_count: 2
        })

      polled_messages = json_response(conn, 200)["data"]
      assert length(polled_messages) == 2

      polled_message_ids = Enum.map(polled_messages, & &1["id"])
      assert message_id_1 in polled_message_ids
      assert message_id_2 in polled_message_ids

      Enum.each(polled_messages, fn message ->
        assert message["status"] == "IN_FLIGHT"
      end)

      conn = delete(conn, ~p"/api/messages/#{message_id_1}")
      assert response(conn, 204)

      conn = delete(conn, ~p"/api/messages/#{message_id_2}")
      assert response(conn, 204)
    end
  end
end
