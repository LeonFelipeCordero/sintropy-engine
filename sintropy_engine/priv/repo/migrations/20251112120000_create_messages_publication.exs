defmodule SintropyEngine.Repo.Migrations.CreateMessagesPublication do
  use Ecto.Migration

  def up do
    execute("DROP PUBLICATION IF EXISTS messages_pub_insert_only")

    execute("""
    CREATE PUBLICATION messages_pub_insert_only
    FOR TABLE messages
    WITH (publish = 'insert')
    """)
  end

  def down do
    execute("DROP PUBLICATION IF EXISTS messages_pub_insert_only")
  end
end
