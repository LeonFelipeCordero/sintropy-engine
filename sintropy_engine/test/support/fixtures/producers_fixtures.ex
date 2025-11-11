defmodule SintropyEngine.ProducersFixtures do
  @moduledoc """
  This module defines test helpers for creating
  entities via the `SintropyEngine.Producers` context.
  """

  @doc """
  Generate a producer.
  """
  def producer_fixture(attrs \\ %{}) do
    {:ok, producer} =
      attrs
      |> Enum.into(%{
        name: "some_name"
      })
      |> SintropyEngine.Producers.create_producer()

    producer
  end
end
