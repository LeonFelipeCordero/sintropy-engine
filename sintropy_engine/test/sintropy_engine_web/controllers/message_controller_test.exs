defmodule SintropyEngineWeb.MessageControllerTest do
  use SintropyEngineWeb.ConnCase

  import SintropyEngine.MessagesFixtures
  import SintropyEngine.ProducersFixtures
  alias SintropyEngine.Messages.Message

  @create_attrs %{
    status: :READY,
    timestamp: ~U[2025-11-11 11:29:00Z],
    headers: "some headers",
    mesage: "some mesage",
    last_delivered: ~U[2025-11-11 11:29:00Z],
    delivered_times: 42
  }
  @update_attrs %{
    status: :IN_FLIGHT,
    timestamp: ~U[2025-11-12 11:29:00Z],
    headers: "some updated headers",
    mesage: "some updated mesage",
    last_delivered: ~U[2025-11-12 11:29:00Z],
    delivered_times: 43
  }
  @invalid_attrs %{
    status: nil,
    timestamp: nil,
    headers: nil,
    routing_key: nil,
    mesage: nil,
    last_delivered: nil,
    delivered_times: nil
  }

  setup %{conn: conn} do
    {:ok, conn: put_req_header(conn, "accept", "application/json")}
  end

  describe "index" do
    test "lists all messages", %{conn: conn} do
      conn = get(conn, ~p"/api/messages")
      assert json_response(conn, 200)["data"] == []
    end
  end

  describe "create message" do
    test "renders message when data is valid", %{conn: conn} do
      %{channel: channel, producer: producer} = producer_fixture()
      routing_key = Enum.at(channel.routing_keys, 0).routing_key

      conn =
        post(conn, ~p"/api/messages",
          message:
            Enum.into(@create_attrs, %{
              channel_id: channel.id,
              producer_id: producer.id,
              routing_key: routing_key
            })
        )

      assert %{"id" => id} = json_response(conn, 201)["data"]

      conn = get(conn, ~p"/api/messages/#{id}")

      assert %{
               "id" => ^id,
               "delivered_times" => 42,
               "headers" => "some headers",
               "last_delivered" => "2025-11-11T11:29:00Z",
               "mesage" => "some mesage",
               "routing_key" => ^routing_key,
               "status" => "READY",
               "timestamp" => "2025-11-11T11:29:00Z"
             } = json_response(conn, 200)["data"]
    end

    test "renders errors when data is invalid", %{conn: conn} do
      conn = post(conn, ~p"/api/messages", message: @invalid_attrs)
      assert json_response(conn, 422)["errors"] != %{}
    end
  end

  describe "update message" do
    setup [:create_message]

    test "renders message when data is valid", %{conn: conn, message: %Message{id: id} = message} do
      conn = put(conn, ~p"/api/messages/#{message}", message: @update_attrs)
      assert %{"id" => ^id} = json_response(conn, 200)["data"]

      conn = get(conn, ~p"/api/messages/#{id}")

      assert %{
               "id" => ^id,
               "delivered_times" => 43,
               "headers" => "some updated headers",
               "last_delivered" => "2025-11-12T11:29:00Z",
               "mesage" => "some updated mesage",
               "status" => "IN_FLIGHT",
               "timestamp" => "2025-11-12T11:29:00Z"
             } = json_response(conn, 200)["data"]
    end

    test "renders errors when data is invalid", %{conn: conn, message: message} do
      conn = put(conn, ~p"/api/messages/#{message}", message: @invalid_attrs)
      assert json_response(conn, 422)["errors"] != %{}
    end
  end

  describe "delete message" do
    setup [:create_message]

    test "deletes chosen message", %{conn: conn, message: message} do
      conn = delete(conn, ~p"/api/messages/#{message}")
      assert response(conn, 204)

      assert_error_sent 404, fn ->
        get(conn, ~p"/api/messages/#{message}")
      end
    end
  end

  defp create_message(_) do
    message = message_fixture()

    %{message: message}
  end
end
