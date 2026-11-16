#!/bin/bash
# Réplica asíncrona: copia base del primario y aplica el WAL con 2 s de retraso,
# para que la lectura desactualizada se reproduzca siempre.
set -e
mkdir -p "$PGDATA"
until pg_isready -h pg-primario -U curso; do sleep 1; done
if [ ! -s "$PGDATA/PG_VERSION" ]; then
  rm -rf "$PGDATA"/*
  pg_basebackup -h pg-primario -U replicador -D "$PGDATA" -R -X stream
  echo "recovery_min_apply_delay = '2s'" >> "$PGDATA/postgresql.auto.conf"
  chmod 700 "$PGDATA"
fi
exec postgres -c hot_standby=on
