defmodule SintropyEngine.Application do
  # See https://hexdocs.pm/elixir/Application.html
  # for more information on OTP Applications
  @moduledoc false

  use Application

  @impl true
  def start(_type, _args) do
    children = [
      SintropyEngineWeb.Telemetry,
      SintropyEngine.Repo,
      {DNSCluster, query: Application.get_env(:sintropy_engine, :dns_cluster_query) || :ignore},
      {Phoenix.PubSub, name: SintropyEngine.PubSub},
      # Start replication consumer for streaming messages
      SintropyEngine.Replication.PGReplicationConsumer,
      # Start a worker by calling: SintropyEngine.Worker.start_link(arg)
      # {SintropyEngine.Worker, arg},
      # Start to serve requests, typically the last entry
      SintropyEngineWeb.Endpoint
    ]

    # See https://hexdocs.pm/elixir/Supervisor.html
    # for other strategies and supported options
    opts = [strategy: :one_for_one, name: SintropyEngine.Supervisor]
    Supervisor.start_link(children, opts)
  end

  # Tell Phoenix to update the endpoint configuration
  # whenever the application is updated.
  @impl true
  def config_change(changed, _new, removed) do
    SintropyEngineWeb.Endpoint.config_change(changed, removed)
    :ok
  end
end
