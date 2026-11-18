#!/usr/bin/env bash
# Solución del taller 11-03 (acortador): pruebas automáticas contra PostgreSQL, y latencia con y sin caché bajo carga (k6).
# Levanta PostgreSQL con Docker Compose y lo baja al terminar. Requiere Docker, JDK 21, k6, jq, curl y python3.
# Variables opcionales: PROYECTO (n11-03), PUERTO_PG (5432), PUERTO_APP (8080).
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; cd "$AQUI"
export PROYECTO=${PROYECTO:-n11-03} PUERTO_PG=${PUERTO_PG:-5432} PUERTO_APP=${PUERTO_APP:-8080}
for c in docker java k6 jq curl python3; do command -v "$c" >/dev/null || { echo "Falta '$c': instálalo (ver README.md)" >&2; exit 2; }; done
paso() { printf '\n== %s\n' "$*"; }
comprobar() { local d=$1; shift; if "$@"; then echo "  OK  $d"; else echo "  FALLA  $d" >&2; exit 1; fi; }
TRABAJO=$(mktemp -d); PID=""
parar() { [ -n "$PID" ] && kill "$PID" 2>/dev/null || true; PID=""; sleep 2; }
bajar() { parar; docker compose -p "$PROYECTO" -f "$AQUI/compose.yaml" down -v --remove-orphans >/dev/null 2>&1 || true; rm -rf "$TRABAJO" "$AQUI/target"; }
trap bajar EXIT
export BD_URL=jdbc:postgresql://localhost:$PUERTO_PG/acortador
docker compose -p "$PROYECTO" -f "$AQUI/compose.yaml" up -d --wait >/dev/null
PSQL() { docker exec -i "$PROYECTO-postgres" psql -U postgres -v ON_ERROR_STOP=1 -qtA "$@"; }
reiniciar_base() { docker exec "$PROYECTO-postgres" psql -U postgres -qc "DROP DATABASE IF EXISTS acortador WITH (FORCE)" -c "CREATE DATABASE acortador" >/dev/null; }

paso "1-2 y 4. Servicio, estrategias y clics asíncronos: pruebas automáticas (contra PostgreSQL real)"
./mvnw -q -B clean package 2>&1 | grep -E "Tests run:|ERROR|estrategia|contador |rangos |aleatorio " | sed 's/^/    /' || true
comprobar "las pruebas pasaron (Base62, comparación de estrategias, integración)" bash -c "[ \$(ls target/surefire-reports/*.txt | wc -l) -ge 4 ] && ! grep -h 'Tests run' target/surefire-reports/*.txt | grep -qE 'Failures: [1-9]|Errors: [1-9]'"

paso "3. Latencia de redirección con y sin caché, bajo carga"
reiniciar_base
JAR=$(ls target/acortador-*.jar | head -1)
arrancar() { # arrancar CACHE
  CLAVE_CODIGOS=clave-del-taller CACHE=$1 PUERTO=$PUERTO_APP java -jar "$JAR" > "$TRABAJO/acortador-$1.log" 2>&1 & PID=$!
  for i in $(seq 1 90); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:$PUERTO_APP/v1/urls/noexiste/estadisticas)" = 404 ] && return 0; sleep 1; done
  tail -20 "$TRABAJO/acortador-$1.log"; echo "no arrancó el acortador" >&2; exit 1
}
arrancar false
PSQL -d acortador -c "INSERT INTO urls(codigo,url_larga) SELECT 'k'||g, 'https://example.com/pagina/'||g FROM generate_series(1,100000) g" -c "ANALYZE urls"
medir() { k6 run -q --summary-export="$TRABAJO/k6-$1.json" -e BASE=http://localhost:$PUERTO_APP "$AQUI/carga.js" >/dev/null 2>&1 || true
  jq -r '"\(.metrics.http_reqs.rate|floor) \(.metrics.http_req_duration.med*100|floor/100) \(.metrics.http_req_duration["p(95)"]*100|floor/100) \(.metrics.checks.fails)"' "$TRABAJO/k6-$1.json"; }
medir calentar >/dev/null
read -r r1 m1 p1 f1 < <(medir sincache)
echo "    sin_cache: rps=$r1 mediana_ms=$m1 p95_ms=$p1 fallos=$f1"
parar; arrancar true
medir calentar >/dev/null
read -r r2 m2 p2 f2 < <(medir concache)
echo "    con_cache: rps=$r2 mediana_ms=$m2 p95_ms=$p2 fallos=$f2"
comprobar "la caché no introduce errores (0 fallos en ambas)" test "$f1$f2" = "00"
comprobar "con caché se atienden más redirecciones por segundo ($r2 > $r1)" test "$r2" -gt "$r1"
comprobar "y la mediana de latencia baja ($m2 ms < $m1 ms)" python3 -c "import sys; sys.exit(0 if float('$m2') < float('$m1') else 1)"
echo; echo "Taller 11-03: todo correcto"
