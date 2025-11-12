defmodule SintropyEngineWeb.ProducerControllerTest do
  use SintropyEngineWeb.ConnCase

  import SintropyEngine.ProducersFixtures
  alias SintropyEngine.ChannelsFixtures
  alias SintropyEngine.Producers.Producer

  @create_attrs %{
    name: "some_name"
  }
  @update_attrs %{
    name: "some_updated_name"
  }
  @invalid_attrs %{name: nil}

  setup %{conn: conn} do
    {:ok, conn: put_req_header(conn, "accept", "application/json")}
  end

  describe "index" do
    test "lists all producers", %{conn: conn} do
      conn = get(conn, ~p"/api/producers")
      assert json_response(conn, 200)["data"] == []
    end
  end

  describe "create producer" do
    test "renders producer when data is valid", %{conn: conn} do
      channel = ChannelsFixtures.channel_standard_queue()

      conn =
        post(conn, ~p"/api/producers",
          producer: Enum.into(@create_attrs, %{channel_id: channel.id})
        )

      assert %{"id" => id} = json_response(conn, 201)["data"]

      conn = get(conn, ~p"/api/producers/#{id}")

      assert %{
               "id" => ^id,
               "name" => "some_name"
             } = json_response(conn, 200)["data"]
    end

    test "renders errors when data is invalid", %{conn: conn} do
      conn = post(conn, ~p"/api/producers", producer: @invalid_attrs)
      assert json_response(conn, 422)["errors"] != %{}
    end
  end

  describe "update producer" do
    setup [:create_producer]

    test "renders producer when data is valid", %{
      conn: conn,
      producer: %Producer{id: id} = producer
    } do
      conn = put(conn, ~p"/api/producers/#{producer}", producer: @update_attrs)
      assert %{"id" => ^id} = json_response(conn, 200)["data"]

      conn = get(conn, ~p"/api/producers/#{id}")

      assert %{
               "id" => ^id,
               "name" => "some_updated_name"
             } = json_response(conn, 200)["data"]
    end

    test "renders errors when data is invalid", %{conn: conn, producer: producer} do
      conn = put(conn, ~p"/api/producers/#{producer}", producer: @invalid_attrs)
      assert json_response(conn, 422)["errors"] != %{}
    end
  end

  describe "delete producer" do
    setup [:create_producer]

    test "deletes chosen producer", %{conn: conn, producer: producer} do
      conn = delete(conn, ~p"/api/producers/#{producer}")
      assert response(conn, 204)

      assert_error_sent 404, fn ->
        get(conn, ~p"/api/producers/#{producer}")
      end
    end
  end

  defp create_producer(_) do
    producer_fixture()
  end
end
