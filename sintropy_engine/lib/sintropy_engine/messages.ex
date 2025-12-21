defmodule SintropyEngine.Messages do
  @moduledoc """
  The Messages context.
  """

  import Ecto.Query, warn: false
  alias Ecto.UUID
  alias SintropyEngine.Messages.EventLog
  alias SintropyEngine.Repo

  alias SintropyEngine.Messages.Message

  @doc """
  Returns the list of messages.

  ## Examples

      iex> list_messages()
      [%Message{}, ...]

  """
  def list_messages do
    Repo.all(Message)
  end

  @doc """
  Returns the list of event logs.

  ## Examples

      iex> list_event_logs()
      [%EventLog{}, ...]

  """
  def list_event_logs do
    Repo.all(EventLog)
  end

  @doc """
  Gets a single message.

  Raises `Ecto.NoResultsError` if the Message does not exist.

  ## Examples

      iex> get_message!(123)
      %Message{}

      iex> get_message!(456)
      ** (Ecto.NoResultsError)

  """
  def get_message!(id), do: Repo.get!(Message, id)

  @doc """
  Gets a single message.

  Raises `Ecto.NoResultsError` if the EventLog does not exist.

  ## Examples

      iex> event_log(123)
      %EventLog{}

      iex> event_log(456)
      ** (Ecto.NoResultsError)

  """
  def get_event_log!(id), do: Repo.get!(EventLog, id)

  @doc """
  Creates a message.

  ## Examples

      iex> create_message(%{field: value})
      {:ok, %Message{}}

      iex> create_message(%{field: bad_value})
      {:error, %Ecto.Changeset{}}

  """
  def create_message(attrs) do
    %Message{}
    |> Message.changeset(attrs)
    |> Repo.insert()
  end

  @doc """
  Updates a message.

  ## Examples

      iex> update_message(message, %{field: new_value})
      {:ok, %Message{}}

      iex> update_message(message, %{field: bad_value})
      {:error, %Ecto.Changeset{}}

  """
  def update_message(%Message{} = message, attrs) do
    message
    |> Message.changeset(attrs)
    |> Repo.update()
  end

  @doc """
  Deletes a message.

  ## Examples

      iex> delete_message(message)
      {:ok, %Message{}}

      iex> delete_message(message)
      {:error, %Ecto.Changeset{}}

  """
  def delete_message(%Message{} = message) do
    Repo.delete(message)
  end

  @doc """
  Returns an `%Ecto.Changeset{}` for tracking message changes.

  ## Examples

      iex> change_message(message)
      %Ecto.Changeset{data: %Message{}}

  """
  def change_message(%Message{} = message, attrs \\ %{}) do
    Message.changeset(message, attrs)
  end

  def poll_standard(channel_id, routing_key, polling_count \\ 1) do
    query = """
        with result as (select id
                from messages
                where channel_id = $1
                  and routing_key = $2
                  and (status = 'READY'
                    or (status = 'IN_FLIGHT'
                        and last_delivered < now() - interval '15 minutes'
                        and delivered_times < 4))
                  and pg_try_advisory_xact_lock($3)
                order by timestamp
                limit $4 for update skip locked)
        update messages
        set status          = 'IN_FLIGHT',
            last_delivered  = now(),
            delivered_times = delivered_times + 1,
            updated_at      = now()
        from result
        where messages.id = result.id
        returning messages.*
    """

    poll(channel_id, routing_key, polling_count, query)
  end

  def poll_fifo(channel_id, routing_key, polling_count \\ 1) do
    query = """
      with result as (select id
              from messages
              where channel_id = $1
                and routing_key = $2
                and status = 'READY'
                and not exists (select 1
                              from messages
                              where channel_id = $1
                              and routing_key = $2
                              and status = 'IN_FLIGHT')
                and pg_try_advisory_xact_lock($3)
              order by timestamp
              limit $4 for update skip locked)
      update messages
      set status          = 'IN_FLIGHT',
          last_delivered  = now(),
          delivered_times = delivered_times + 1,
          updated_at      = now()
      from result
      where messages.id in (result.id)
      returning messages.*;
    """

    poll(channel_id, routing_key, polling_count, query)
  end

  def poll(channel_id, routing_key, polling_count, query) do
    hash = :erlang.phash2({channel_id, routing_key})
    params = [channel_id, routing_key, hash, polling_count]

    case Repo.query(query, params) do
      {:ok, %{rows: rows, columns: columns}} ->
        Enum.map(rows, fn row ->
          data = Map.new(Enum.zip(columns, row))

          %Message{
            id: UUID.cast!(data["id"]),
            timestamp: DateTime.from_naive!(data["timestamp"], "Etc/UTC"),
            routing_key: data["routing_key"],
            message: data["message"],
            headers: data["headers"],
            status: String.to_atom(data["status"]),
            last_delivered: DateTime.from_naive!(data["last_delivered"], "Etc/UTC"),
            delivered_times: data["delivered_times"],
            channel_id: data["channel_id"],
            producer_id: data["producer_id"],
            inserted_at: DateTime.from_naive!(data["inserted_at"], "Etc/UTC"),
            updated_at: DateTime.from_naive!(data["updated_at"], "Etc/UTC")
          }
        end)
        |> Enum.sort_by(& &1.timestamp)

      {:error, error} ->
        {:error, error}
    end
  end

  def dequeue(%Message{status: :READY} = message) do
    raise "Mesasge can't be in status ready when deuqueuing: message id: #{message.id}"
  end

  def dequeue(%Message{status: status} = message) when status in [:IN_FLIGHT, :FAILED] do
    delete_message(message)
  end
end
