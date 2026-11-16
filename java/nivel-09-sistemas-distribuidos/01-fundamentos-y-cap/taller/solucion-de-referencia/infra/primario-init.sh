#!/bin/bash
# Usuario de replicación y permiso en pg_hba para que la réplica haga pg_basebackup y transmita WAL.
set -e
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "CREATE ROLE replicador WITH REPLICATION LOGIN PASSWORD 'replica';"
echo "host replication replicador all scram-sha-256" >> "$PGDATA/pg_hba.conf"
