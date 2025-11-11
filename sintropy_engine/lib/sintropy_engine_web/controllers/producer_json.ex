defmodule SintropyEngineWeb.ProducerJSON do
  alias SintropyEngine.Producers.Producer

  @doc """
  Renders a list of producers.
  """
  def index(%{producers: producers}) do
    %{data: for(producer <- producers, do: data(producer))}
  end

  @doc """
  Renders a single producer.
  """
  def show(%{producer: producer}) do
    %{data: data(producer)}
  end

  defp data(%Producer{} = producer) do
    %{
      id: producer.id,
      name: producer.name
    }
  end
end
