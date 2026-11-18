#!/usr/bin/env bash
# Solución del taller 11-05 (venta de boletas): levanta PostgreSQL, ejecuta BoletasTest y lo baja todo al terminar.
# Requiere Docker y JDK 21. Variables opcionales: PROYECTO (n11-05), PUERTO_PG (5432).
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; cd "$AQUI"
export PROYECTO=${PROYECTO:-n11-05} PUERTO_PG=${PUERTO_PG:-5432}
export BOLETAS_HOST=localhost:$PUERTO_PG
for c in docker java; do command -v "$c" >/dev/null || { echo "Falta '$c': instálalo (ver README.md)" >&2; exit 2; }; done
bajar() { docker compose -p "$PROYECTO" -f "$AQUI/compose.yaml" down -v --remove-orphans >/dev/null 2>&1 || true; rm -rf "$AQUI/target"; }
trap bajar EXIT
docker compose -p "$PROYECTO" -f "$AQUI/compose.yaml" up -d --wait >/dev/null
./mvnw -q -B clean test 2>&1 | grep -vE "^\s+at |SLF4J|WARNING|HikariPool|INFO" | sed 's/^/    /' || true
if ls target/surefire-reports/*BoletasTest.txt >/dev/null 2>&1 && grep -h 'Tests run' target/surefire-reports/*BoletasTest.txt | grep -q 'Failures: 0, Errors: 0'; then
  echo "  OK  todas las pruebas de BoletasTest pasaron"
else echo "  FALLA  BoletasTest" >&2; exit 1; fi
