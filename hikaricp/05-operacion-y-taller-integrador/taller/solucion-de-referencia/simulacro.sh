#!/usr/bin/env bash
# Taller 05, parte 3: provoca las siete situaciones con carga continua y GRABA el panel (herramientas/panel.py) y los registros.
# El diagnóstico se hace después, solo sobre lo grabado, con herramientas/diagnostico.py. Uso: simulacro.sh situacion...
set -euo pipefail
source "$(dirname "$0")/herramientas/lib.sh"
AQUI="$RAIZ_VER"
OUT="$AQUI/resultados"; mkdir -p "$OUT/series"
JAR="$AQUI/target/taller-05-1.0.0.jar"
export DB_PASSWORD='Tienda2026!'
BASE_RPS=${BASE_RPS:-200}
declare -a PIDS=()
instancia() {   # instancia puerto [args...]: arranca una instancia; su registro queda en $SERIE.puerto.log
  local puerto=$1; shift
  java -jar "$JAR" --server.port="$puerto" --spring.datasource.url="$DB_URL" "$@" > "$SERIE.$puerto.log" 2>&1 &
  PIDS+=($!)
}
esperar() { for _ in $(seq 1 120); do [ "$(curl -s -o /dev/null -w '%{http_code}' "localhost:$1/actuator/health")" != "000" ] && return 0; sleep 0.5; done; return 1; }
limpiar() { for p in ${PIDS[@]+"${PIDS[@]}"}; do kill "$p" 2> /dev/null || true; wait "$p" 2> /dev/null || true; done; PIDS=(); }
trap 'limpiar; jobs -p | xargs kill 2> /dev/null || true' EXIT
KN=0
carga() {   # carga bases rate duracion [hist]: deja el resumen de k6 en $SERIE.k6-N.json (lo que vieron los usuarios)
  KN=$((KN + 1))
  BASES="$1" RATE="$2" DURACION="$3" HIST="${4:-5}" k6 run -q --summary-export "$SERIE.k6-$KN.json" "$RAIZ_VER/herramientas/carga05.js" > /dev/null 2>&1 &
}
indice() { psqlv -q -c "$1" ; }
PRE=10          # segundos de normalidad antes de provocar

situacion() {
  local nombre=$1 total=${2:-60}
  KN=0
  SERIE="$OUT/series/$nombre"; rm -f "$SERIE".*
  local puertos=(46801 46802 46803) paneles=()
  for p in 46801 46802 46803 46804 46805 46806; do paneles+=("$p:$SERIE.$p.log"); done
  echo "== $nombre"
  curl -s "localhost:46799/demora?ms=50" > /dev/null
  psqlv -q -c "create index if not exists historial_por_cliente on historial (cliente_id)"
  for p in "${puertos[@]}"; do instancia "$p"; done
  for p in "${puertos[@]}"; do esperar "$p" || { echo "no arrancó $p"; tail -30 "$SERIE.$p.log"; exit 1; }; done
  for p in "${puertos[@]}"; do for _ in $(seq 1 30); do curl -s -o /dev/null "localhost:$p/lectura/1"; done; done
  local bases="http://localhost:46801,http://localhost:46802,http://localhost:46803"
  python3 -I "$RAIZ_VER/herramientas/panel.py" "$SERIE.jsonl" "$total" "${paneles[@]}" &
  local PANEL=$!
  carga "$bases" "$BASE_RPS" "${total}s" 5
  sleep "$PRE"
  date +%s.%N > "$SERIE.t0"
  case "$nombre" in
    fuga)       BASES="$bases" RATE=30 DURACION="$((total - PRE))s" HIST=0 FUGA=100 k6 run -q "$RAIZ_VER/herramientas/carga05.js" > /dev/null 2>&1 & ;;
    retencion)  curl -s "localhost:46799/demora?ms=3000" > /dev/null ;;
    consulta-lenta) indice "drop index historial_por_cliente" ;;
    capacidad)  carga "$bases" "$((BASE_RPS * 10))" "$((total - PRE))s" 5 ;;
    caida)      dc stop db > /dev/null 2>&1; echo "$(date +%s.%N)" > "$SERIE.parada"
                sleep 30; dc start db > /dev/null 2>&1; echo "$(date +%s.%N)" > "$SERIE.arranque" ;;
    cortadas)   psqlv -q -c "select pg_terminate_backend(pid) from pg_stat_activity where usename = 'tienda'" > /dev/null ;;
    instancias) for p in 46804 46805 46806; do instancia "$p"; done ;;
  esac
  wait "$PANEL"
  limpiar; jobs -p | xargs kill 2> /dev/null || true; sleep 1
  curl -s "localhost:46799/demora?ms=50" > /dev/null
  psqlv -q -c "create index if not exists historial_por_cliente on historial (cliente_id)"
  dc up -d --wait db > /dev/null 2>&1
}

for s in "$@"; do situacion "$s" "$([ "$s" = caida ] && echo 100 || echo 60)"; done
