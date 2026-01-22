FROM postgres:18

COPY ./config/pg_hba.conf /etc/postgresql/pg_hba.conf

RUN apt-get update && apt-get install -y postgresql-18-wal2json && rm -rf /var/lib/apt/lists/*

CMD ["postgres", "-c", "wal_level=logical", "-c", "max_wal_senders=15", "-c", "max_replication_slots=15", "-c", "max_logical_replication_workers=15", "-c", "max_worker_processes=16", "-c", "hba_file=/etc/postgresql/pg_hba.conf"]

