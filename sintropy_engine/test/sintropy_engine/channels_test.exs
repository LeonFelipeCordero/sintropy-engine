defmodule SintropyEngine.ChannelsTest do
  use SintropyEngine.DataCase

  alias SintropyEngine.Channels

  describe "channels" do
    alias SintropyEngine.Channels.Channel

    import SintropyEngine.ChannelsFixtures

    @invalid_attrs %{name: nil, channel_type: nil}

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

    test "create_channel/1 with valid data creates a channel with type queue" do
      valid_attrs = %{name: "some_name", channel_type: :QUEUE}

      assert {:ok, %Channel{} = channel} = Channels.create_channel(valid_attrs)
      assert channel.name == "some_name"
      assert channel.channel_type == :QUEUE
    end

    test "create_channel/1 with valid data creates a channel with type stream" do
      valid_attrs = %{name: "some_name", channel_type: :STREAM}

      assert {:ok, %Channel{} = channel} = Channels.create_channel(valid_attrs)
      assert channel.name == "some_name"
      assert channel.channel_type == :STREAM
    end

    test "create_channel/1 with invalid data returns error changeset" do
      assert {:error, %Ecto.Changeset{}} = Channels.create_channel(@invalid_attrs)
    end

    test "create_channel/1 fail when name contains blank spaces" do
      assert {:error, %Ecto.Changeset{}} =
               Channels.create_channel(%{name: "some name", channel_type: :QUEUE})
    end

    test "update_channel/2 with valid data updates the channel" do
      channel = channel_fixture()
      update_attrs = %{name: "some_updated_name", channel_type: :STREAM}

      assert {:ok, %Channel{} = channel} = Channels.update_channel(channel, update_attrs)
      assert channel.name == "some_updated_name"
      assert channel.channel_type == :STREAM
    end

    test "update_channel/2 with invalid data returns error changeset" do
      channel = channel_fixture()
      assert {:error, %Ecto.Changeset{}} = Channels.update_channel(channel, @invalid_attrs)
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
  end
end
