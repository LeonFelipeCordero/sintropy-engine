defmodule SintropyEngineWeb.ProducerController do
  use SintropyEngineWeb, :controller

  alias SintropyEngine.Producers
  alias SintropyEngine.Producers.Producer

  action_fallback SintropyEngineWeb.FallbackController

  def index(conn, _params) do
    producers = Producers.list_producers()
    render(conn, :index, producers: producers)
  end

  def create(conn, %{"producer" => producer_params}) do
    with {:ok, %Producer{} = producer} <- Producers.create_producer(producer_params) do
      conn
      |> put_status(:created)
      |> put_resp_header("location", ~p"/api/producers/#{producer}")
      |> render(:show, producer: producer)
    end
  end

  def show(conn, %{"id" => id}) do
    producer = Producers.get_producer!(id)
    render(conn, :show, producer: producer)
  end

  def update(conn, %{"id" => id, "producer" => producer_params}) do
    producer = Producers.get_producer!(id)

    with {:ok, %Producer{} = producer} <- Producers.update_producer(producer, producer_params) do
      render(conn, :show, producer: producer)
    end
  end

  def delete(conn, %{"id" => id}) do
    producer = Producers.get_producer!(id)

    with {:ok, %Producer{}} <- Producers.delete_producer(producer) do
      send_resp(conn, :no_content, "")
    end
  end
end
