#!/usr/bin/env bash
set -e

postgres -c wal_level=logical
# -c max_wal_senders=1 \
# -c max_replication_slots=2 \
# -c max_logical_replication_workers=1 \
# -c max_worker_processes=2
# -c hba_file=/etc/postgresql/pg_hba.conf \
