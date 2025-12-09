defmodule SintropyEngine.MessagesFixtures do
  @moduledoc """
  This module defines test helpers for creating
  entities via the `SintropyEngine.Messages` context.
  """
  import SintropyEngine.ProducersFixtures

  @doc """
  Generate a message.
  """
  def message_fixture(attrs \\ %{}) do
    %{channel: channel, producer: producer} = producer_fixture()

    {:ok, message} =
      attrs
      |> Enum.into(%{
        delivered_times: 42,
        headers: "some headers",
        last_delivered: ~U[2025-11-11 11:29:00Z],
        mesage: "some mesage",
        routing_key: Enum.at(channel.routing_keys, 0).routing_key,
        status: :READY,
        timestamp: ~U[2025-11-11 11:29:00Z],
        channel_id: channel.id,
        producer_id: producer.id
      })
      |> SintropyEngine.Messages.create_message()

    message
  end

  def message_wiht_not_existing_producer_fixture(attrs \\ %{}) do
    %{channel: channel, producer: _} = producer_fixture()

    attrs
    |> Enum.into(%{
      delivered_times: 42,
      headers: "some headers",
      last_delivered: ~U[2025-11-11 11:29:00Z],
      mesage: "some mesage",
      routing_key: Enum.at(channel.routing_keys, 0).routing_key,
      status: :READY,
      timestamp: ~U[2025-11-11 11:29:00Z],
      channel_id: channel.id,
      producer_id: Ecto.UUID.generate()
    })
    |> SintropyEngine.Messages.create_message()
  end

  def message_wiht_not_existing_channel_fixture(attrs \\ %{}) do
    %{channel: channel, producer: producer} = producer_fixture()

    attrs
    |> Enum.into(%{
      delivered_times: 42,
      headers: "some headers",
      last_delivered: ~U[2025-11-11 11:29:00Z],
      mesage: "some mesage",
      routing_key: Enum.at(channel.routing_keys, 0).routing_key,
      status: :READY,
      timestamp: ~U[2025-11-11 11:29:00Z],
      channel_id: Ecto.UUID.generate(),
      producer_id: producer.id
    })
    |> SintropyEngine.Messages.create_message()
  end

  def message_wiht_not_existing_routing_key_fixture(attrs \\ %{}) do
    %{channel: channel, producer: producer} = producer_fixture()

    attrs
    |> Enum.into(%{
      delivered_times: 42,
      headers: "some headers",
      last_delivered: ~U[2025-11-11 11:29:00Z],
      mesage: "some mesage",
      routing_key: "test_12345",
      status: :READY,
      timestamp: ~U[2025-11-11 11:29:00Z],
      channel_id: channel.id,
      producer_id: producer.id
    })
    |> SintropyEngine.Messages.create_message()
  end
end
