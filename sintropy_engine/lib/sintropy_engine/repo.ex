defmodule SintropyEngine.Repo do
  use Ecto.Repo,
    otp_app: :sintropy_engine,
    adapter: Ecto.Adapters.Postgres
end
