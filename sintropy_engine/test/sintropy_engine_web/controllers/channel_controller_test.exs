defmodule SintropyEngineWeb.ChannelControllerTest do
  use SintropyEngineWeb.ConnCase

  import SintropyEngine.ChannelsFixtures
  alias SintropyEngine.Channels.Channel

  @create_attrs %{
    name: "some name",
    channel_id: "7488a646-e31f-11e4-aace-600308960662",
    channel_type: :QUEUE
  }
  @update_attrs %{
    name: "some updated name",
    channel_id: "7488a646-e31f-11e4-aace-600308960668",
    channel_type: :STREAM
  }
  @invalid_attrs %{name: nil, channel_id: nil, channel_type: nil}

  setup %{conn: conn} do
    {:ok, conn: put_req_header(conn, "accept", "application/json")}
  end

  describe "index" do
    test "lists all channels", %{conn: conn} do
      conn = get(conn, ~p"/api/channels")
      assert json_response(conn, 200)["data"] == []
    end
  end

  describe "create channel" do
    test "renders channel when data is valid", %{conn: conn} do
      conn = post(conn, ~p"/api/channels", channel: @create_attrs)
      assert %{"id" => id} = json_response(conn, 201)["data"]

      conn = get(conn, ~p"/api/channels/#{id}")

      assert %{
               "id" => ^id,
               "channel_id" => "7488a646-e31f-11e4-aace-600308960662",
               "channel_type" => "QUEUE",
               "name" => "some name"
             } = json_response(conn, 200)["data"]
    end

    test "renders errors when data is invalid", %{conn: conn} do
      conn = post(conn, ~p"/api/channels", channel: @invalid_attrs)
      assert json_response(conn, 422)["errors"] != %{}
    end
  end

  describe "update channel" do
    setup [:create_channel]

    test "renders channel when data is valid", %{conn: conn, channel: %Channel{id: id} = channel} do
      conn = put(conn, ~p"/api/channels/#{channel}", channel: @update_attrs)
      assert %{"id" => ^id} = json_response(conn, 200)["data"]

      conn = get(conn, ~p"/api/channels/#{id}")

      assert %{
               "id" => ^id,
               "channel_id" => "7488a646-e31f-11e4-aace-600308960668",
               "channel_type" => "STREAM",
               "name" => "some updated name"
             } = json_response(conn, 200)["data"]
    end

    test "renders errors when data is invalid", %{conn: conn, channel: channel} do
      conn = put(conn, ~p"/api/channels/#{channel}", channel: @invalid_attrs)
      assert json_response(conn, 422)["errors"] != %{}
    end
  end

  describe "delete channel" do
    setup [:create_channel]

    test "deletes chosen channel", %{conn: conn, channel: channel} do
      conn = delete(conn, ~p"/api/channels/#{channel}")
      assert response(conn, 204)

      assert_error_sent 404, fn ->
        get(conn, ~p"/api/channels/#{channel}")
      end
    end
  end

  defp create_channel(_) do
    channel = channel_fixture()

    %{channel: channel}
  end
end
