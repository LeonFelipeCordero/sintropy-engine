defmodule SintropyEngine.PollingFixtures do
  import SintropyEngine.ChannelsFixtures
  import SintropyEngine.ProducersFixtures

  alias SintropyEngine.Messages
  alias Ecto.UUID

  require Logger

  def launch_producers(enumerable, testing_data) do
    enumerable
    |> Task.async_stream(
      fn _ ->
        routing = random_routing(testing_data)
        message = build_message(routing)

        {:ok, saved_message} = Messages.create_message(message)
        saved_message
      end,
      max_concurrency: System.schedulers_online(),
      timeout: 10_000
    )
    |> Enum.map(fn {:ok, %{id: id}} -> id end)
  end

  def launch_consumers(enumerable, testing_data, parent_pid) do
    enumerable
    |> Enum.map(fn _ ->
      testing_data
      |> Enum.map(fn {_, %{channel: channel}} -> channel end)
      |> Enum.flat_map(fn channel ->
        Enum.map(channel.routing_keys, fn routing_key ->
          %{channel_id: channel.id, routing_key: routing_key.routing_key}
        end)
      end)
      |> Task.async_stream(
        fn routing ->
          launch_consumer(routing.channel_id, routing.routing_key, parent_pid)
        end,
        max_concurrency: System.schedulers_online(),
        timeout: 10_000
      )
      |> Stream.run()
    end)
  end

  def launch_consumer(channel_id, routing_key, test_pid, empty_receives \\ 0)

  def launch_consumer(channel_id, routing_key, test_pid, empty_receives)
      when empty_receives <= 5 do
    polling_count = :rand.uniform(5)
    messages = Messages.poll_fifo(UUID.dump!(channel_id), routing_key, polling_count)

    case length(messages) do
      0 ->
        Logger.info(
          "Lauching consumer: #{channel_id}_#{routing_key} with counter #{empty_receives + 1}"
        )

        Process.sleep(20)
        launch_consumer(channel_id, routing_key, test_pid, empty_receives + 1)

      _ ->
        unique_routing =
          messages
          |> Enum.map(fn m -> "#{UUID.cast!(m.channel_id)}_#{m.routing_key}" end)
          |> Enum.uniq()

        if(length(unique_routing) > 1) do
          raise "Message polling got messages from different routes than #{channel_id}_#{routing_key}"
        end

        Logger.info("Got #{length(messages)} in the reponse")

        messages
        |> Enum.each(fn m ->
          send(test_pid, {:message, m})
          Messages.dequeue(m)
        end)

        Process.sleep(10)
        launch_consumer(channel_id, routing_key, test_pid, empty_receives)
    end
  end

  def launch_consumer(channel_id, routing_key, _test_pid, 6) do
    Logger.info("Consumer finished for routing: #{channel_id}_#{routing_key}")
    :ok
  end

  def testing_data_standard() do
    channel_one =
      channel_standard_queue(%{
        name: UUID.generate(),
        routing_keys: [
          %{routing_key: "test.1.1"},
          %{routing_key: "test.1.2"},
          %{routing_key: "test.1.3"}
        ]
      })

    channel_two =
      channel_standard_queue(%{
        name: UUID.generate(),
        routing_keys: [
          %{routing_key: "test.2.1"},
          %{routing_key: "test.2.2"}
        ]
      })

    channel_three =
      channel_standard_queue(%{
        name: UUID.generate(),
        routing_keys: [
          %{routing_key: "test.3.1"}
        ]
      })

    producer_one = producer_without_channel_fixture(channel_one.id)
    producer_two = producer_without_channel_fixture(channel_two.id)
    producer_three = producer_without_channel_fixture(channel_three.id)

    %{
      channel_producer_one: %{channel: channel_one, producer: producer_one},
      channel_producer_two: %{channel: channel_two, producer: producer_two},
      channel_producer_three: %{channel: channel_three, producer: producer_three}
    }
  end

  def testing_data_fifo() do
    channel_one =
      channel_fifo_queue(%{
        name: UUID.generate(),
        routing_keys: [
          %{routing_key: "test.1.1"},
          %{routing_key: "test.1.2"},
          %{routing_key: "test.1.3"}
        ]
      })

    channel_two =
      channel_fifo_queue(%{
        name: UUID.generate(),
        routing_keys: [
          %{routing_key: "test.2.1"},
          %{routing_key: "test.2.2"}
        ]
      })

    channel_three =
      channel_fifo_queue(%{
        name: UUID.generate(),
        routing_keys: [
          %{routing_key: "test.3.1"}
        ]
      })

    producer_one = producer_without_channel_fixture(channel_one.id)
    producer_two = producer_without_channel_fixture(channel_two.id)
    producer_three = producer_without_channel_fixture(channel_three.id)

    %{
      channel_producer_one: %{channel: channel_one, producer: producer_one},
      channel_producer_two: %{channel: channel_two, producer: producer_two},
      channel_producer_three: %{channel: channel_three, producer: producer_three}
    }
  end

  def random_routing(testing_data) do
    {_, %{channel: channel, producer: producer}} = testing_data |> Enum.random()

    %{
      channel_id: channel.id,
      producer_id: producer.id,
      routing_key: (channel.routing_keys |> Enum.random()).routing_key
    }
  end

  def keys(testing_data) do
    testing_data
    |> Enum.flat_map(fn {_, %{channel: channel}} ->
      Enum.map(channel.routing_keys, fn rt ->
        "#{channel.id}_#{rt.routing_key}"
      end)
    end)
  end

  def build_message(routing) do
    %{
      delivered_times: 0,
      headers: "some headers",
      last_delivered: nil,
      message: "some message",
      status: :READY,
      timestamp: DateTime.now!("Etc/UTC"),
      channel_id: routing.channel_id,
      producer_id: routing.producer_id,
      routing_key: routing.routing_key
    }
  end
end
