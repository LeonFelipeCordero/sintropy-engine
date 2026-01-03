defmodule SintropyEngine.SingleChannelFifoPollingE2eTest do
  use SintropyEngineWeb.ConnCase

  setup %{conn: conn} do
    {:ok, conn: put_req_header(conn, "accept", "application/json")}
  end

  describe "E2E test for testing a FIFO channel" do
    import SintropyEngine.E2eFixtures

    test "full E2E flow: create channel, publish message, poll, and dequeue via API", %{
      conn: conn
    } do
      channel_id = create_fifo_channel(conn)
      producer_id = create_create_producer(conn, channel_id)

      message_attrs = %{
        channel_id: channel_id,
        producer_id: producer_id,
        routing_key: "test.fifo",
        message: "Test message",
        headers: "Content-Type: text/plain",
        status: "READY",
        timestamp: DateTime.now!("Etc/UTC") |> DateTime.to_iso8601()
      }

      message_id = send_message(conn, message_attrs)

      polled_messages =
        poll_messages(conn, %{
          channel_id: channel_id,
          routing_key: "test.fifo",
          polling_count: 1
        })

      assert length(polled_messages) == 1
      polled_message = List.first(polled_messages)
      assert polled_message["id"] == message_id
      assert polled_message["status"] == "IN_FLIGHT"

      polled_messages_again =
        poll_messages(conn, %{
          channel_id: channel_id,
          routing_key: "test.fifo",
          polling_count: 1
        })

      assert polled_messages_again == []

      dequeue_message(conn, message_id)

      assert_error_sent 404, fn ->
        get(conn, ~p"/api/messages/#{message_id}")
      end
    end

    test "publish two messages and poll both in a single call", %{conn: conn} do
      channel_id = create_fifo_channel(conn)
      producer_id = create_create_producer(conn, channel_id)

      message_attrs_1 = %{
        channel_id: channel_id,
        producer_id: producer_id,
        routing_key: "test.fifo",
        message: "First test message",
        headers: "Content-Type: text/plain",
        timestamp: DateTime.now!("Etc/UTC") |> DateTime.to_iso8601()
      }

      message_attrs_2 = %{
        channel_id: channel_id,
        producer_id: producer_id,
        routing_key: "test.fifo",
        message: "Second test message",
        headers: "Content-Type: text/plain",
        timestamp: DateTime.now!("Etc/UTC") |> DateTime.to_iso8601()
      }

      message_id_1 = send_message(conn, message_attrs_1)
      message_id_2 = send_message(conn, message_attrs_2)

      polled_messages =
        poll_messages(
          conn,
          %{
            channel_id: channel_id,
            routing_key: "test.fifo",
            polling_count: 2
          }
        )

      polled_message_ids = Enum.map(polled_messages, & &1["id"])
      assert message_id_1 == Enum.at(polled_message_ids, 0)
      assert message_id_2 == Enum.at(polled_message_ids, 1)

      dequeue_message(conn, message_id_1)
      dequeue_message(conn, message_id_2)
    end
  end
end
