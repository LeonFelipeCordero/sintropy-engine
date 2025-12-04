defmodule SintropyEngine.ProducersTest do
  use SintropyEngine.DataCase

  alias SintropyEngine.Producers

  describe "producers" do
    alias SintropyEngine.Producers.Producer

    import SintropyEngine.ProducersFixtures

    @invalid_attrs %{name: nil}

    test "list_producers/0 returns all producers" do
      %{producer: producer} = producer_fixture()
      assert Producers.list_producers() == [producer]
    end

    test "get_producer!/1 returns the producer with given id" do
      %{producer: producer} = producer_fixture()
      assert Producers.get_producer!(producer.id) == producer
    end

    test "create_producer/1 with valid data creates a producer" do
      %{channel: channel, producer: producer} = producer_fixture()
      assert producer.name == "some_name"
      assert producer.channel_id == channel.id
    end

    test "create_producer/1 with invalid data returns error changeset" do
      assert {:error, %Ecto.Changeset{}} = Producers.create_producer(@invalid_attrs)
    end

    test "create_producer/1 with non existing channel should fail" do
      assert {:error, %Ecto.Changeset{}} = producer_without_existing_channel_fixture()
    end

    test "update_producer/2 with valid data updates the producer" do
      %{producer: producer} = producer_fixture()
      update_attrs = %{name: "some_updated_name"}

      assert {:ok, %Producer{} = producer} = Producers.update_producer(producer, update_attrs)
      assert producer.name == "some_updated_name"
    end

    test "update_producer/2 with invalid data returns error changeset" do
      %{producer: producer} = producer_fixture()
      assert {:error, %Ecto.Changeset{}} = Producers.update_producer(producer, @invalid_attrs)
      assert producer == Producers.get_producer!(producer.id)
    end

    test "delete_producer/1 deletes the producer" do
      %{producer: producer} = producer_fixture()
      assert {:ok, %Producer{}} = Producers.delete_producer(producer)
      assert_raise Ecto.NoResultsError, fn -> Producers.get_producer!(producer.id) end
    end

    test "change_producer/1 returns a producer changeset" do
      %{producer: producer} = producer_fixture()
      assert %Ecto.Changeset{} = Producers.change_producer(producer)
    end
  end
end
