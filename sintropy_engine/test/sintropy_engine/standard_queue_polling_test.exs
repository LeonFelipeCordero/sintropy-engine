defmodule SintropyEngine.StandardQueueTest do
  import SintropyEngine.ChannelsFixtures
  import SintropyEngine.ProducersFixtures
  import SintropyEngine.MessagesFixtures
  import SintropyEngine.PollingFixtures

  alias SintropyEngine.Messages
  alias Ecto.UUID

  use SintropyEngine.DataCase

  require Logger

  describe "polling from standard queue" do
    test "should queue a message and poll" do
      message = message_fixture()

      channel_id = UUID.dump!(message.channel_id)

      polled_message = Messages.poll_standard(channel_id, message.routing_key)
      assert length(polled_message) == 1
      polled = List.first(polled_message)
      assert message.id == polled.id
      assert message.timestamp == polled.timestamp
      assert message.routing_key == polled.routing_key
      assert message.message == polled.message
    end

    test "should not return anything if the message is consumed" do
      message = message_fixture()

      channel_id = UUID.dump!(message.channel_id)

      Messages.poll_standard(channel_id, message.routing_key)
      polled_message = Messages.poll_standard(channel_id, message.routing_key)

      assert polled_message == []
    end

    test "should queue two messages and poll one by one" do
      %{channel: channel, producer: producer} = producer_fixture()
      message1 = message_fixture(%{channel_id: channel.id, producer_id: producer.id})
      message2 = message_fixture(%{channel_id: channel.id, producer_id: producer.id})

      channel_id1 = UUID.dump!(message1.channel_id)
      channel_id2 = UUID.dump!(message2.channel_id)

      polled_message1 = Messages.poll_standard(channel_id1, message1.routing_key)
      polled_message2 = Messages.poll_standard(channel_id2, message2.routing_key)

      assert length(polled_message1) == 1
      assert length(polled_message2) == 1

      polled1 = List.first(polled_message1)
      assert message1.id == polled1.id
      polled2 = List.first(polled_message2)
      assert message2.id == polled2.id
    end

    test "should queue a message and try to poll five" do
      message = message_fixture()

      channel_id = UUID.dump!(message.channel_id)

      polled_message = Messages.poll_standard(channel_id, message.routing_key, 5)

      assert length(polled_message) == 1
      polled = List.first(polled_message)
      assert message.id == polled.id
    end

    test "should poll from an empty queue and do not fail" do
      channel = channel_standard_queue()

      channel_id = UUID.dump!(channel.id)

      polled_message = Messages.poll_standard(channel_id, "default.routing.key")

      assert polled_message == []
    end

    test "should poll messages in chronological order one by one" do
      %{channel: channel, producer: producer} = producer_fixture()
      routing_key = Enum.at(channel.routing_keys, 0).routing_key
      channel_id = UUID.dump!(channel.id)

      message1 =
        message_fixture(%{
          channel_id: channel.id,
          producer_id: producer.id,
          routing_key: routing_key,
          timestamp: DateTime.add(DateTime.now!("Etc/UTC"), -3)
        })

      message2 =
        message_fixture(%{
          channel_id: channel.id,
          producer_id: producer.id,
          routing_key: routing_key,
          timestamp: DateTime.add(DateTime.now!("Etc/UTC"), -2)
        })

      message3 =
        message_fixture(%{
          channel_id: channel.id,
          producer_id: producer.id,
          routing_key: routing_key,
          timestamp: DateTime.add(DateTime.now!("Etc/UTC"), -1)
        })

      polled_message1 = Messages.poll_standard(channel_id, routing_key)
      polled_message2 = Messages.poll_standard(channel_id, routing_key)
      polled_message3 = Messages.poll_standard(channel_id, routing_key)

      assert length(polled_message1) == 1
      assert length(polled_message2) == 1
      assert length(polled_message3) == 1

      assert List.first(polled_message1).id == message1.id
      assert List.first(polled_message2).id == message2.id
      assert List.first(polled_message3).id == message3.id
    end

    test "should poll messages in chronological order all at once" do
      %{channel: channel, producer: producer} = producer_fixture()
      routing_key = Enum.at(channel.routing_keys, 0).routing_key
      channel_id = UUID.dump!(channel.id)

      message1 =
        message_fixture(%{
          channel_id: channel.id,
          producer_id: producer.id,
          routing_key: routing_key,
          timestamp: DateTime.add(DateTime.now!("Etc/UTC"), -3)
        })

      message2 =
        message_fixture(%{
          channel_id: channel.id,
          producer_id: producer.id,
          routing_key: routing_key,
          timestamp: DateTime.add(DateTime.now!("Etc/UTC"), -2)
        })

      message3 =
        message_fixture(%{
          channel_id: channel.id,
          producer_id: producer.id,
          routing_key: routing_key,
          timestamp: DateTime.add(DateTime.now!("Etc/UTC"), -1)
        })

      polled_messages = Messages.poll_standard(channel_id, routing_key, 3)

      assert length(polled_messages) == 3
      assert Enum.map(polled_messages, & &1.id) == [message1.id, message2.id, message3.id]
    end

    test "should not poll messages from other routing key" do
      %{channel: channel, producer: producer} = producer_fixture()
      routing_key1 = Enum.at(channel.routing_keys, 0).routing_key

      routing_key2 = "test.2"

      SintropyEngine.Channels.create_routing_key(%{
        channel_id: channel.id,
        routing_key: routing_key2
      })

      channel_id = UUID.dump!(channel.id)

      message1 =
        message_fixture(%{
          channel_id: channel.id,
          producer_id: producer.id,
          routing_key: routing_key1
        })

      message2 =
        message_fixture(%{
          channel_id: channel.id,
          producer_id: producer.id,
          routing_key: routing_key2
        })

      polled_message1 = Messages.poll_standard(channel_id, routing_key1, 2)
      polled_message2 = Messages.poll_standard(channel_id, routing_key2, 2)

      assert length(polled_message1) == 1
      assert length(polled_message2) == 1
      assert List.first(polled_message1).id == message1.id
      assert List.first(polled_message2).id == message2.id
    end

    test "should not poll messages from other channel" do
      message1 = message_fixture()
      message2 = message_fixture()

      channel_id1 = UUID.dump!(message1.channel_id)
      channel_id2 = UUID.dump!(message2.channel_id)

      polled_message1 = Messages.poll_standard(channel_id1, message1.routing_key, 2)
      polled_message2 = Messages.poll_standard(channel_id2, message2.routing_key, 2)

      assert length(polled_message1) == 1
      assert length(polled_message2) == 1
      assert List.first(polled_message1).id == message1.id
      assert List.first(polled_message2).id == message2.id
    end

    test "should not pull a message that has been poll more than three times" do
      message = message_fixture(%{status: :IN_FLIGHT, delivered_times: 4})

      polled_messages =
        Messages.poll_standard(UUID.dump!(message.channel_id), message.routing_key)

      assert length(polled_messages) == 0
    end

    test "should not pull a message that has been marked as failed" do
      message = message_fixture(%{status: :FAILED})

      polled_messages =
        Messages.poll_standard(UUID.dump!(message.channel_id), message.routing_key)

      assert length(polled_messages) == 0
    end

    test "should not pull a message is lass than 15 minutes pulled" do
      message = message_fixture()

      channel_id = UUID.dump!(message.channel_id)

      first_poll = Messages.poll_standard(channel_id, message.routing_key)
      assert length(first_poll) == 1

      second_poll = Messages.poll_standard(channel_id, message.routing_key)
      assert length(second_poll) == 0

      Messages.update_message(message, %{
        last_delivered: DateTime.now!("Etc/UTC") |> DateTime.add(-30, :minute)
      })

      third_poll = Messages.poll_standard(channel_id, message.routing_key)
      assert length(third_poll) == 1
    end

    test "should dequeue a message that is processed and mark it in the event log" do
      message = message_fixture()

      Messages.poll_standard(UUID.dump!(message.channel_id), message.routing_key)
      Messages.delete_message(message)

      assert Messages.list_messages() == []
      event_logs = Messages.list_event_logs()
      assert length(event_logs) == 1
      assert List.first(event_logs).processed == true
    end

    test "should not dequeue a message that is on ready" do
      message = message_fixture()

      assert_raise RuntimeError,
                   "Mesasge can't be in status ready when deuqueuing: message id: #{message.id}",
                   fn ->
                     Messages.dequeue(message)
                   end
    end

    test "should dequeue a message that is on in_flight" do
      message = message_fixture(%{status: :IN_FLIGHT})
      Messages.dequeue(message)
      assert Messages.list_messages() == []
    end

    test "should dequeue a message that is on failed" do
      message = message_fixture(%{status: :FAILED})
      Messages.dequeue(message)
      assert Messages.list_messages() == []
    end

    # TODO: I can't make this one work because the function received a full object
    # To Make it work, crete a message and override the struct channel_id field,
    # then pass it to dequue
    # test "should fail to dequeue if message is not found" do
    #   assert_raise Ecto.NoResultsError, fn ->
    #     Messages.dequeue(Ecto.UUID.generate())
    #   end
    # end

    test "concurrent processing of messages" do
      testing_data = testing_data_standard()

      sent_messages = 1..1000 |> launch_producers(testing_data)

      1..10 |> launch_consumers(testing_data, self())

      keys = keys(testing_data)

      received_messages =
        sent_messages
        |> Enum.reduce(Map.new(keys, &{&1, []}), fn _, acc ->
          receive do
            {:message, message} ->
              key = "#{UUID.cast!(message.channel_id)}_#{message.routing_key}"
              Map.update(acc, key, [message], fn list -> [message | list] end)

            _ ->
              raise "Unknown message received..."
          after
            2_000 ->
              Logger.info("No more messages after 2 seconds")
              nil
          end
        end)
        |> Map.new(fn {k, v} -> {k, Enum.reverse(v)} end)

      all_event_logs = Messages.list_event_logs() |> Enum.filter(fn el -> el.processed end)
      received_count = length(received_messages |> Enum.flat_map(fn {_, list} -> list end))

      assert length(sent_messages) == received_count
      assert length(all_event_logs) == received_count

      received_messages
      |> Enum.each(fn {key, messages} ->
        event_log_ids =
          all_event_logs
          |> Enum.filter(&("#{UUID.cast!(&1.channel_id)}_#{&1.routing_key}" == key))
          |> Enum.map(& &1.id)
          |> Enum.sort()

        messages_ids = messages |> Enum.map(& &1.id) |> Enum.sort()

        assert messages_ids == event_log_ids
      end)
    end

    test "single processing of routing pair is always in chronological order" do
      testing_data = testing_data_standard()

      sent_messages = 1..1000 |> launch_producers(testing_data)

      1..1 |> launch_consumers(testing_data, self())

      keys = keys(testing_data)

      received_messages =
        sent_messages
        |> Enum.reduce(Map.new(keys, &{&1, []}), fn _, acc ->
          receive do
            {:message, message} ->
              key = "#{UUID.cast!(message.channel_id)}_#{message.routing_key}"
              Map.update(acc, key, [message], fn list -> [message | list] end)

            _ ->
              raise "Unknown message received..."
          after
            2_000 ->
              Logger.info("No more messages after 2 seconds")
              nil
          end
        end)
        |> Map.new(fn {k, v} -> {k, Enum.reverse(v)} end)

      all_event_logs = Messages.list_event_logs() |> Enum.filter(fn el -> el.processed end)
      received_count = length(received_messages |> Enum.flat_map(fn {_, list} -> list end))

      assert length(sent_messages) == received_count
      assert length(all_event_logs) == received_count

      received_messages
      |> Enum.each(fn {key, messages} ->
        event_log_ids =
          all_event_logs
          |> Enum.filter(&("#{UUID.cast!(&1.channel_id)}_#{&1.routing_key}" == key))
          |> Enum.map(& &1.id)
          |> Enum.sort()

        messages_ids = messages |> Enum.map(& &1.id) |> Enum.sort()

        assert messages_ids == event_log_ids
      end)
    end
  end
end
