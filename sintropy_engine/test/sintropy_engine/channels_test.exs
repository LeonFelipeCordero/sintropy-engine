defmodule SintropyEngine.ChannelsTest do
  use SintropyEngine.DataCase

  alias SintropyEngine.Channels

  describe "channels" do
    alias SintropyEngine.Channels.Channel

    import SintropyEngine.ChannelsFixtures

    @invalid_attrs %{name: nil, channel_type: nil, routing_keys: [], queue: nil}
    @valid_attrs %{
      name: "some_name",
      channel_type: :QUEUE,
      routing_keys: [%{routing_key: "test.1"}],
      queue: %{consumption_type: :STANDARD}
    }

    test "list_channels/0 returns all channels" do
      channel = channel_fixture()
      assert Channels.list_channels() == [channel]
    end

    test "get_channel!/1 returns the channel with given id" do
      channel = channel_fixture()
      assert Channels.get_channel!(channel.id) == channel
    end

    test "find_channel_by_id/1 returns the channel with given name" do
      channel = channel_fixture()
      assert Channels.find_channel_by_name(channel.name) == channel
    end

    test "find_channel_by_id/1 returns nil when channel not found by name" do
      channel_fixture()
      assert Channels.find_channel_by_name("fake name") == nil
    end

    test "create_channel/1 with valid data creates a channel with type queue standard" do
      assert {:ok, %Channel{} = channel} =
               Enum.into(%{queue: %{consumption_type: :STANDARD}}, @valid_attrs)
               |> Channels.create_channel()

      assert channel.name == "some_name"
      assert channel.channel_type == :QUEUE
      assert length(channel.routing_keys) == 1
      assert List.first(channel.routing_keys).routing_key == "test.1"
      assert channel.queue.consumption_type == :STANDARD
    end

    test "create_channel/1 with valid data creates a channel with type queue fifo" do
      assert {:ok, %Channel{} = channel} =
               Enum.into(%{queue: %{consumption_type: :FIFO}}, @valid_attrs)
               |> Channels.create_channel()

      assert channel.name == "some_name"
      assert channel.channel_type == :QUEUE
      assert length(channel.routing_keys) == 1
      assert List.first(channel.routing_keys).routing_key == "test.1"
      assert channel.queue.consumption_type == :FIFO
    end

    test "create_channel/1 with valid data creates a channel with type stream" do
      assert {:ok, %Channel{} = channel} =
               Enum.into(%{channel_type: :STREAM}, @valid_attrs)
               |> Map.delete(:queue)
               |> Channels.create_channel()

      assert channel.name == "some_name"
      assert channel.channel_type == :STREAM
      assert length(channel.routing_keys) == 1
      assert List.first(channel.routing_keys).routing_key == "test.1"
      assert !Ecto.assoc_loaded?(channel.queue)
    end

    test "create_channel/1 with invalid data returns error changeset" do
      assert {:error, %Ecto.Changeset{}} = Channels.create_channel(@invalid_attrs)
    end

    test "create_channel/1 fail when name contains blank spaces" do
      assert {:error, %Ecto.Changeset{}} =
               Enum.into(%{name: "some name"}, @valid_attrs)
               |> Channels.create_channel()
    end

    test "create_channel/1 fail when routing_key contains blank spaces" do
      assert {:error, %Ecto.Changeset{}} =
               Enum.into(%{routing_keys: ["tests 1"]}, @valid_attrs)
               |> Channels.create_channel()
    end

    test "update_channel/2 with valid data updates the channel" do
      channel = channel_fixture()

      update_attrs = %{
        name: "some_updated_name",
        channel_type: :QUEUE
      }

      assert {:ok, %Channel{} = channel} = Channels.update_channel(channel, update_attrs)
      assert channel.name == "some_updated_name"
      assert channel.channel_type == :QUEUE
    end

    test "update_channel/2 with invalid data returns error changeset" do
      channel = channel_fixture()

      update_attrs = %{
        name: nil,
        channel_type: nil
      }

      assert {:error, %Ecto.Changeset{}} = Channels.update_channel(channel, update_attrs)
      assert channel == Channels.get_channel!(channel.id)
    end

    test "delete_channel/1 deletes the channel" do
      channel = channel_fixture()
      assert {:ok, %Channel{}} = Channels.delete_channel(channel)
      assert_raise Ecto.NoResultsError, fn -> Channels.get_channel!(channel.id) end
    end

    test "change_channel/1 returns a channel changeset" do
      channel = channel_fixture()
      assert %Ecto.Changeset{} = Channels.change_channel(channel)
    end

    test "add_routing_key/2 creates a new routing key linked to the channel" do
    end
  end
end
