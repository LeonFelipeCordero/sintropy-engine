defmodule SintropyEngine.SingleChannelStandarPollingE2eTest do
  use SintropyEngineWeb.ConnCase

  import SintropyEngine.E2eFixtures

  setup %{conn: conn} do
    conn = put_req_header(conn, "accept", "application/json")
    channel_id = create_standard_channel(conn)
    producer_id = create_create_producer(conn, channel_id)

    {:ok, conn: conn, channel_id: channel_id, producer_id: producer_id}
  end

  describe "E2E test for testing a STANDARD channel" do
    test "full E2E flow: create channel, publish message, poll, and dequeue via API", %{
      conn: conn,
      channel_id: channel_id,
      producer_id: producer_id
    } do

      message_attrs = %{
        channel_id: channel_id,
        producer_id: producer_id,
        routing_key: "test.standard",
        message: "Test message",
        headers: "Content-Type: text/plain",
        status: "READY",
        timestamp: DateTime.now!("Etc/UTC") |> DateTime.to_iso8601()
      }

      message_id = send_message(conn, message_attrs)

      polled_messages =
        poll_messages(conn, %{
          channel_id: channel_id,
          routing_key: "test.standard",
          polling_count: 1
        })

      assert length(polled_messages) == 1
      polled_message = List.first(polled_messages)
      assert polled_message["id"] == message_id

      polled_messages_again =
        poll_messages(conn, %{
          channel_id: channel_id,
          routing_key: "test.standard",
          polling_count: 1
        })

      assert polled_messages_again == []

      dequeue_message(conn, message_id)

      assert_error_sent 404, fn ->
        get(conn, ~p"/api/messages/#{message_id}")
      end
    end

    test "publish two messages, poll both ina single call", %{
      conn: conn,
      channel_id: channel_id,
      producer_id: producer_id
    } do

      message_attrs_1 = %{
        channel_id: channel_id,
        producer_id: producer_id,
        routing_key: "test.standard",
        message: "First test message",
        headers: "Content-Type: test/plain",
        timestamp: DateTime.now!("Etc/UTC") |> DateTime.to_iso8601()
      }

      message_attrs_2 = %{
        channel_id: channel_id,
        producer_id: producer_id,
        routing_key: "test.standard",
        message: "Second test message",
        headers: "Content-Type: test/plain",
        timestamp: DateTime.now!("Etc/UTC") |> DateTime.to_iso8601()
      }

      message_id_1 = send_message(conn, message_attrs_1)
      message_id_2 = send_message(conn, message_attrs_2)

      polled_messages =
        poll_messages(conn, %{
          channel_id: channel_id,
          routing_key: "test.standard",
          polling_count: 2
        })

      assert length(polled_messages) == 2

      polled_messages_ids = Enum.map(polled_messages, & &1["id"])
      assert message_id_1 == Enum.at(polled_messages_ids, 0)
      assert message_id_2 == Enum.at(polled_messages_ids, 1)

      dequeue_message(conn, message_id_1)
      dequeue_message(conn, message_id_2)
    end

    # test "polling from STREAM channel returns error via API", %{conn: conn} do
    #   stream_channel_attrs = %{
    #     name: "stream_test_channel",
    #     channel_type: "STREAM",
    #     routing_keys: [%{routing_key: "test.stream"}]
    #   }
    #
    #   conn = post(conn, ~p"/api/channels", channel: stream_channel_attrs)
    #   %{"id" => channel_id} = json_response(conn, 201)["data"]
    #
    #   conn =
    #     post(conn, ~p"/api/messages/poll", %{
    #       channel_id: channel_id,
    #       routing_key: "test.stream",
    #       polling_count: 1
    #     })
    #
    #   assert json_response(conn, 400)["error"] == "Polling is only available for QUEUE channels"
    # end
  end
end
