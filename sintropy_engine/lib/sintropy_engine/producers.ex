defmodule SintropyEngine.Producers do
  @moduledoc """
  The Producers context.
  """

  import Ecto.Query, warn: false
  alias SintropyEngine.Repo

  alias SintropyEngine.Producers.Producer

  @doc """
  Returns the list of producers.

  ## Examples

      iex> list_producers()
      [%Producer{}, ...]

  """
  def list_producers do
    Repo.all(Producer)
  end

  @doc """
  Gets a single producer.

  Raises `Ecto.NoResultsError` if the Producer does not exist.

  ## Examples

      iex> get_producer!(123)
      %Producer{}

      iex> get_producer!(456)
      ** (Ecto.NoResultsError)

  """
  def get_producer!(id), do: Repo.get!(Producer, id)

  @doc """
  Creates a producer.

  ## Examples

      iex> create_producer(%{field: value})
      {:ok, %Producer{}}

      iex> create_producer(%{field: bad_value})
      {:error, %Ecto.Changeset{}}

  """
  def create_producer(attrs) do
    %Producer{}
    |> Producer.changeset(attrs)
    |> Repo.insert()
  end

  @doc """
  Updates a producer.

  ## Examples

      iex> update_producer(producer, %{field: new_value})
      {:ok, %Producer{}}

      iex> update_producer(producer, %{field: bad_value})
      {:error, %Ecto.Changeset{}}

  """
  def update_producer(%Producer{} = producer, attrs) do
    producer
    |> Producer.changeset(attrs)
    |> Repo.update()
  end

  @doc """
  Deletes a producer.

  ## Examples

      iex> delete_producer(producer)
      {:ok, %Producer{}}

      iex> delete_producer(producer)
      {:error, %Ecto.Changeset{}}

  """
  def delete_producer(%Producer{} = producer) do
    Repo.delete(producer)
  end

  @doc """
  Returns an `%Ecto.Changeset{}` for tracking producer changes.

  ## Examples

      iex> change_producer(producer)
      %Ecto.Changeset{data: %Producer{}}

  """
  def change_producer(%Producer{} = producer, attrs \\ %{}) do
    Producer.changeset(producer, attrs)
  end
end
