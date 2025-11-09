defmodule SintropyEngineWeb.PageController do
  use SintropyEngineWeb, :controller

  def home(conn, _params) do
    render(conn, :home)
  end
end
