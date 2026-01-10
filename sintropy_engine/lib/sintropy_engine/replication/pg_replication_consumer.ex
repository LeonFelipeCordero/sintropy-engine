defmodule SintropyEngine.Replication.PGReplicationConsumer do
  @moduledoc """
  PostgreSQL logical replication consumer that captures changes from the messages table
  and broadcasts them via PubSub for websocket streaming.

  Uses PostgreSQL replication protocol to receive changes as a stream (push-based),
  similar to the Kotlin implementation. PostgreSQL pushes changes directly to this process.
  """
  use Postgrex.ReplicationConnection
  require Logger

  alias SintropyEngine.Channels
  alias SintropyEngine.PubSub
  alias Ecto.UUID
  alias SintropyEngine.Messages.Message

  @replication_slot "messages_slot"
  @publication_name "messages_pub_insert_only"
  @output_plugin "wal2json"
  @epoch DateTime.to_unix(~U[2000-01-01 00:00:00Z], :microsecond)

  def start_link(opts) do
    config = get_repo_config()

    connection_opts = [
      hostname: config[:hostname],
      port: config[:port],
      username: config[:username],
      password: config[:password],
      database: config[:database],
      auto_reconnect: true
    ]

    Postgrex.ReplicationConnection.start_link(__MODULE__, :ok, connection_opts ++ opts)
  end

  @impl true
  def init(:ok) do
    {:ok, %{step: :disconnected}}
  end

  @impl true
  def handle_connect(state) do
    Logger.info("Dropping replication slot: #{@replication_slot}")

    drop_query = """
    select pg_drop_replication_slot('#{@replication_slot}')
    where exists (
      select 1 from pg_replication_slots where slot_name = '#{@replication_slot}'
    );
    """

    {:query, drop_query, %{state | step: :create_slot}}
  end

  @impl true
  def handle_result(%Postgrex.Error{} = error, %{step: :create_slot} = state) do
    Logger.debug("Error dropping slot (may not exist): #{inspect(error)}")

    Logger.info("Creating replication slot: #{@replication_slot}")

    create_query = """
      CREATE_REPLICATION_SLOT #{@replication_slot} TEMPORARY LOGICAL #{@output_plugin} NOEXPORT_SNAPSHOT
    """

    {:query, create_query, %{state | step: :start_replication}}
  end

  @impl true
  def handle_result([%Postgrex.Result{}], %{step: :create_slot} = state) do
    Logger.info("Creating replication slot: #{@replication_slot}")

    create_query = """
      CREATE_REPLICATION_SLOT #{@replication_slot} TEMPORARY LOGICAL #{@output_plugin} NOEXPORT_SNAPSHOT
    """

    {:query, create_query, %{state | step: :start_replication}}
  end

  @impl true
  def handle_result([%Postgrex.Result{}], %{step: :start_replication} = state) do
    Logger.info("Starting replication stream from slot #{@replication_slot}")

    query =
      ~s{START_REPLICATION SLOT #{@replication_slot} LOGICAL 0/0 ("actions" 'insert', "add-tables" 'public.messages')}

    {:stream, query, [], %{state | step: :streaming}}
  end

  defp parse_integer(value) when is_integer(value), do: value
  defp parse_integer(value) when is_binary(value), do: String.to_integer(value)

  defp parse_datetime(nil), do: nil
  defp parse_datetime("null"), do: nil

  defp parse_datetime(datetime_string) do
    datetime_string
    |> String.replace(" ", "T")
    |> NaiveDateTime.from_iso8601!()
    |> DateTime.from_naive!("Etc/UTC")
  end

  @impl true
  def handle_data(<<?w, _wal_start::64, _wal_end::64, _clock::64, rest::binary>>, state) do
    case Jason.decode(rest) do
      {:ok, %{"change" => changes}} when is_list(changes) ->
        changes
        |> Enum.filter(fn
          %{"kind" => "insert", "table" => "messages"} -> true
          _ -> false
        end)
        |> Enum.each(fn change ->
          column_names = Map.get(change, "columnnames", [])
          column_values = Map.get(change, "columnvalues", [])

          if column_names != [] and column_values != [] do
            record = Enum.zip(column_names, column_values) |> Map.new()

            msg = %Message{
              id: record["id"] && UUID.cast!(record["id"]),
              timestamp: parse_datetime(record["timestamp"]),
              routing_key: record["routing_key"],
              message: record["message"],
              headers: record["headers"],
              status: String.to_atom(record["status"]),
              last_delivered: parse_datetime(record["last_delivered"]),
              delivered_times: parse_integer(record["delivered_times"]),
              channel_id: record["channel_id"],
              producer_id: record["producer_id"]
            }

            Logger.debug("Replicated Message struct: #{inspect(msg)}")
          end
        end)

        {:noreply, state}

      {:ok, other} ->
        Logger.debug("Unexpected wal2json payload: #{inspect(other)}")
        {:noreply, state}

      {:error, reason} ->
        Logger.debug(
          "Failed to decode wal2json payload: #{inspect(reason)}, data: #{String.slice(rest, 0, 200)}"
        )

        {:noreply, state}
    end
  end

  def handle_data(<<?k, wal_end::64, _clock::64, reply>>, state) do
    messages =
      case reply do
        1 -> [<<?r, wal_end + 1::64, wal_end + 1::64, wal_end + 1::64, current_time()::64, 0>>]
        0 -> []
      end

    {:noreply, messages, state}
  end

  @epoch DateTime.to_unix(~U[2000-01-01 00:00:00Z], :microsecond)
  defp current_time(), do: System.os_time(:microsecond) - @epoch
  #
  # @impl true
  # def handle_result([%Postgrex.Error{} = error], %{step: :creating_slot}) do
  #   Logger.error("Failed to create replication slot: #{inspect(error)}")
  #   {:disconnect, "Failed to create replication slot"}
  # end
  #
  # @impl true
  # def handle_result([%Postgrex.Error{} = error], %{step: :start_replication}) do
  #   Logger.error("Failed to start replication in slot: #{inspect(error)}")
  #   {:disconnect, "Failed to start replication"}
  # end
  #
  # @impl true
  # def handle_result([%Postgrex.Error{} = error], _state) do
  #   Logger.error("Replication error: #{inspect(error)}")
  #   {:disconnect, "Replication error: #{inspect(error)}"}
  # end

  defp get_repo_config do
    repo_config = Application.get_env(:sintropy_engine, SintropyEngine.Repo, [])

    [
      hostname: repo_config[:hostname] || System.get_env("DB_HOST") || "localhost",
      port: repo_config[:port] || (System.get_env("DB_PORT") || "5432") |> String.to_integer(),
      username: repo_config[:username] || System.get_env("DB_USER") || "postgres",
      password: repo_config[:password] || System.get_env("DB_PASSWORD") || "postgres",
      database: repo_config[:database] || System.get_env("DB_NAME") || "postgres"
    ]
  end
end
