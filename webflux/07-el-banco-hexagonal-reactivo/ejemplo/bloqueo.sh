#!/usr/bin/env bash
# Una fila bloqueada en PostgreSQL y cuatro transferencias que la esperan. ¿Qué le pasa a una consulta que no tiene nada que ver?
# Uso: ./bloqueo.sh 8094   (antes: JDBC)      ./bloqueo.sh 8095   (después: R2DBC)
# Necesita el entorno levantado (docker compose -p webflux up -d, desde ../../entorno).
set -euo pipefail
PUERTO=${1:-8094}
COMPOSE="docker compose -p webflux -f $(dirname "$0")/../../entorno/compose.yaml"

abrir() {
  curl -s -X POST "localhost:$PUERTO/cuentas" -H 'Content-Type: application/json' \
    -d "{\"titular\":\"$1\",\"centavos\":$2}" | sed -E 's/.*"id":"([^"]+)".*/\1/'
}
ANA=$(abrir Ana 100000000)
LUIS=$(abrir Luis 0)
EVA=$(abrir Eva 5000000)

# Otra sesión (un proceso por lotes, un informe con FOR UPDATE...) retiene la fila de Ana durante 5 segundos
$COMPOSE exec -T postgres psql -q -U banco -d banco \
  -c "begin; select id from cuenta where id = '$ANA' for update; select pg_sleep(5); commit;" > /dev/null &
until [ "$($COMPOSE exec -T postgres psql -tA -U banco -d banco -c \
  "select count(*) from pg_stat_activity where state = 'active' and query like '%pg_sleep(5)%' and pid <> pg_backend_pid()")" = "1" ]; do
  sleep 0.2                                                  # espera a que el bloqueo esté tomado de verdad
done

# Cuatro transferencias desde Ana, una por hilo del bucle de eventos: las cuatro esperan a ese bloqueo
for i in 1 2 3 4; do
  curl -s -o /dev/null -w "transferencia $i: %{http_code} en %{time_total} s\n" -X POST "localhost:$PUERTO/transferencias" \
    -H "Idempotency-Key: bloqueo-$PUERTO-$RANDOM-$i" -H 'Content-Type: application/json' \
    -d "{\"origen\":\"$ANA\",\"destino\":\"$LUIS\",\"centavos\":100000}" &
done
sleep 0.5

# Mientras tanto, alguien consulta la cuenta de Eva, que nadie tiene bloqueada
curl -s -o /dev/null -w "consulta de Eva:  %{http_code} en %{time_total} s\n" "localhost:$PUERTO/cuentas/$EVA"
wait
