defmodule SintropyEngine.MessagesTest do
  use SintropyEngine.DataCase

  alias SintropyEngine.Messages

  describe "messages" do
    alias SintropyEngine.Messages.Message

    import SintropyEngine.MessagesFixtures
    import SintropyEngine.ProducersFixtures

    @invalid_attrs %{
      status: nil,
      timestamp: nil,
      headers: nil,
      routing_key: nil,
      mesage: nil,
      last_delivered: nil,
      delivered_times: nil
    }

    test "list_messages/0 returns all messages" do
      message = message_fixture()
      assert Messages.list_messages() == [message]
    end

    test "get_message!/1 returns the message with given id" do
      message = message_fixture()
      assert Messages.get_message!(message.id) == message
    end

    test "create_message/1 with valid data creates a message" do
      %{channel: channel, producer: producer} = producer_fixture()

      valid_attrs = %{
        status: :READY,
        timestamp: ~U[2025-11-11 11:29:00Z],
        headers: "some headers",
        routing_key: "some routing_key",
        mesage: "some mesage",
        last_delivered: ~U[2025-11-11 11:29:00Z],
        delivered_times: 42,
        channel_id: channel.id,
        producer_id: producer.id
      }

      assert {:ok, %Message{} = message} = Messages.create_message(valid_attrs)
      assert message.status == :READY
      assert message.timestamp == ~U[2025-11-11 11:29:00Z]
      assert message.headers == "some headers"
      assert message.routing_key == "some routing_key"
      assert message.mesage == "some mesage"
      assert message.last_delivered == ~U[2025-11-11 11:29:00Z]
      assert message.delivered_times == 42
    end

    test "create_message/1 with invalid data returns error changeset" do
      assert {:error, %Ecto.Changeset{}} = Messages.create_message(@invalid_attrs)
    end

    test "create_message/1 with non existing producer returns error changeset" do
      assert {:error, %Ecto.Changeset{}} = message_wiht_not_existing_producer_fixture()
    end

    test "create_message/1 with non existing channel returns error changeset" do
      assert {:error, %Ecto.Changeset{}} = message_wiht_not_existing_channel_fixture()
    end

    test "create_message/1 with non existing routing key returns error changeset" do
      assert {:error, %Ecto.Changeset{}} = Messages.create_message()
    end

    test "update_message/2 with valid data updates the message" do
      message = message_fixture()

      update_attrs = %{
        status: :IN_FLIGHT,
        timestamp: ~U[2025-11-12 11:29:00Z],
        headers: "some updated headers",
        routing_key: "some updated routing_key",
        mesage: "some updated mesage",
        last_delivered: ~U[2025-11-12 11:29:00Z],
        delivered_times: 43
      }

      assert {:ok, %Message{} = message} = Messages.update_message(message, update_attrs)
      assert message.status == :IN_FLIGHT
      assert message.timestamp == ~U[2025-11-12 11:29:00Z]
      assert message.headers == "some updated headers"
      assert message.routing_key == "some updated routing_key"
      assert message.mesage == "some updated mesage"
      assert message.last_delivered == ~U[2025-11-12 11:29:00Z]
      assert message.delivered_times == 43
    end

    test "update_message/2 with invalid data returns error changeset" do
      message = message_fixture()
      assert {:error, %Ecto.Changeset{}} = Messages.update_message(message, @invalid_attrs)
      assert message == Messages.get_message!(message.id)
    end

    test "delete_message/1 deletes the message" do
      message = message_fixture()
      assert {:ok, %Message{}} = Messages.delete_message(message)
      assert_raise Ecto.NoResultsError, fn -> Messages.get_message!(message.id) end
    end

    test "change_message/1 returns a message changeset" do
      message = message_fixture()
      assert %Ecto.Changeset{} = Messages.change_message(message)
    end
  end
end
