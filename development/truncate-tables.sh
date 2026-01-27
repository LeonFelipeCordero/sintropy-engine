#!/bin/bash

# Truncate all tables in the database except flyway_schema_history
# Usage: ./truncate-tables.sh [host] [port] [database] [user]

HOST=${1:-localhost}
PORT=${2:-5432}
DATABASE=${3:-postgres}
USER=${4:-postgres}

echo "Truncating all tables in $DATABASE on $HOST:$PORT..."

# Get all table names excluding flyway_schema_history
TABLES=$(PGPASSWORD=postgres psql -h "$HOST" -p "$PORT" -U "$USER" -d "$DATABASE" -t -c "
SELECT string_agg(table_name, ', ')
FROM information_schema.tables
WHERE table_schema = 'public'
AND table_type = 'BASE TABLE'
AND table_name NOT IN ('flyway_schema_history');
")

# Trim whitespace
TABLES=$(echo "$TABLES" | xargs)

if [ -z "$TABLES" ]; then
    echo "No tables found to truncate."
    exit 0
fi

echo "Tables to truncate: $TABLES"

# Truncate all tables with CASCADE to handle foreign key constraints
PGPASSWORD=postgres psql -h "$HOST" -p "$PORT" -U "$USER" -d "$DATABASE" -c "TRUNCATE TABLE $TABLES CASCADE;"

echo "Done."
