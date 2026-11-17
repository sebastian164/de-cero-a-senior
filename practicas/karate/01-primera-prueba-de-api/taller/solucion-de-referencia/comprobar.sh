#!/usr/bin/env bash
# Solución de referencia del taller Karate 01. Comprueba los criterios contra una Bitácora real, arrancada aquí en el puerto 48080.
# Pasos: 2 y 8 (la suite dos veces seguidas), 9 (un escenario solo, por nombre y por etiqueta), 5 de 02 (sin clave falla
# con un mensaje claro), 10 (arranque lento sin espera fija), 11 (el informe HTML de un fallo) y "sin direcciones ni claves".
set -euo pipefail
aqui=$(cd "$(dirname "$0")" && pwd)
# --- Funciones para arrancar y parar copias de Bitácora fuera del repositorio ---------------------------------
# Compila docker-kubernetes/app/api/Servidor.java en una carpeta temporal: no deja clases ni datos dentro del curso.
#   iniciar_bitacora <nombre> <puerto> [VAR=valor ...]   arranca en segundo plano, con su propio DIRECTORIO_DATOS
#   esperar_lista <puerto> [segundos]                     espera a que /salud/listo responda 200
#   parar_bitacora <nombre>                               la detiene y espera
#   parar_todas_las_bitacoras                             detiene las que haya arrancado este proceso
# Variable opcional: BITACORA_FUENTE (otro Servidor.java, por ejemplo una copia modificada).
BITACORA_ORIGINAL="$(cd "$aqui/../../../../.." && pwd)/docker-kubernetes/app/api/Servidor.java"
[ -f "$BITACORA_ORIGINAL" ] || { echo "No encuentro $BITACORA_ORIGINAL" >&2; exit 2; }
BITACORA_TRABAJO=$(mktemp -d "${TMPDIR:-/tmp}/bitacora-taller.XXXXXX")
declare -a _bitacoras=()
iniciar_bitacora() {
  local nombre=$1 puerto=$2
  shift 2
  local fuente=${BITACORA_FUENTE:-$BITACORA_ORIGINAL}
  local dir="$BITACORA_TRABAJO/$nombre"
  rm -rf "$dir"
  mkdir -p "$dir/clases" "$dir/datos"
  javac -d "$dir/clases" "$fuente" || return 1
  (
    cd "$dir" || exit 1
    env PUERTO="$puerto" DIRECTORIO_DATOS="$dir/datos" "$@" java -cp "$dir/clases" Servidor >"$dir/registro.log" 2>&1 &
    echo $! >"$dir/pid"
  )
  _bitacoras+=("$nombre")
}
esperar_lista() {
  local puerto=$1 limite=${2:-30} i
  for ((i = 0; i < limite * 5; i++)); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$puerto/salud/listo")" = 200 ]; then return 0; fi
    sleep 0.2
  done
  echo "Bitácora no quedó lista en el puerto $puerto" >&2
  return 1
}
parar_bitacora() {
  local dir="$BITACORA_TRABAJO/$1"
  [ -f "$dir/pid" ] || return 0
  local pid
  pid=$(cat "$dir/pid")
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 50); do kill -0 "$pid" 2>/dev/null || break; sleep 0.1; done
  kill -9 "$pid" 2>/dev/null || true
  rm -f "$dir/pid"
}
parar_todas_las_bitacoras() {
  local nombre
  for nombre in "${_bitacoras[@]:-}"; do [ -n "$nombre" ] && parar_bitacora "$nombre"; done
  _bitacoras=()
}
# --------------------------------------------------------------------------------------------------------------
limpiar() { parar_todas_las_bitacoras; rm -rf "$BITACORA_TRABAJO" "$aqui/target"; }
trap limpiar EXIT
cd "$aqui"
CLAVE="clave-$RANDOM-$RANDOM"
fallos=0
mvn_suite() { mvn -q test -Dbitacora.clave="$CLAVE" "$@" 2>&1; }
resumen() { grep -E '^ESCENARIOS' || echo "(sin resumen)"; }
ok() { echo "  ok     $*"; }
falla() { echo "  FALLA  $*"; fallos=$((fallos + 1)); }

if grep -rnE 'https?://|localhost|clave-|secreta' --include='*.feature' src/test/java; then
  falla "hay direcciones o claves en los .feature"
else
  ok "ningún .feature tiene direcciones ni claves"
fi

iniciar_bitacora karate01 48080 CLAVE_API="$CLAVE"
esperar_lista 48080
# Antes de nada, con la API recién arrancada (lista de notas vacía): el escenario de la ruta por prefijo no puede depender de notas ajenas.
if salida=$(mvn -q test -Dbitacora.clave="$CLAVE" '-Descenario=una ruta que empieza como una conocida responde como la conocida' 2>&1); then ok "con la API vacía, el escenario de la ruta por prefijo pasa solo: $(resumen <<<"$salida")"; else falla "el escenario de la ruta por prefijo depende de datos ajenos"; echo "$salida" | tail -20; fi

for vuelta in 1 2; do
  if salida=$(mvn_suite); then ok "suite completa, vuelta $vuelta: $(resumen <<<"$salida")"; else falla "suite, vuelta $vuelta"; echo "$salida" | tail -30; fi
done
if salida=$(mvn_suite '-Descenario=una nota guardada aparece en la lista'); then ok "un escenario por nombre: $(resumen <<<"$salida")"; else falla "escenario por nombre"; fi
if salida=$(mvn_suite '-Detiquetas=@humo'); then ok "solo @humo: $(resumen <<<"$salida")"; else falla "@humo"; fi

if salida=$(mvn -q test 2>&1); then
  falla "sin clave la suite debía fallar"
elif grep -q 'Falta la clave de la API' <<<"$salida"; then
  ok "sin clave falla con el mensaje: Falta la clave de la API"
else
  falla "sin clave falla, pero sin el mensaje claro"
fi

if mvn_suite '-Detiquetas=@falla' >/dev/null; then
  falla "el escenario provocado debía fallar"
else
  informe=target/karate-reports/feature-html/bitacora.falla-a-proposito.html
  if [ -f "$informe" ] && grep -q '9.9.9' "$informe" && grep -q 'match failed' "$informe"; then
    ok "un fallo provocado queda en $(basename "$informe") con el paso, lo esperado y el motivo"
  else
    falla "el informe HTML no cuenta el fallo"
  fi
fi
parar_bitacora karate01

# Paso 10, primero sin esperar: la suite lanzada contra una API que todavía arranca falla (503).
iniciar_bitacora karate01-sin-esperar 48084 CLAVE_API="$CLAVE" RETARDO_ARRANQUE_MS=20000
until curl -s -o /dev/null "http://localhost:48084/salud/vivo"; do sleep 0.2; done
if salida=$(mvn_suite -Dbitacora.url=http://localhost:48084 -Dbitacora.esperar=no); then
  falla "sin esperar, la suite debía fallar contra una API que arranca"
else
  ok "sin esperar a que esté lista, la suite falla: $(resumen <<<"$salida")"
fi
parar_bitacora karate01-sin-esperar

# Paso 10: arranque lento. Primero se comprueba qué responde mientras arranca; después la suite completa, sin esperas fijas.
inicio=$(date +%s)
iniciar_bitacora karate01-lenta 48083 CLAVE_API="$CLAVE" RETARDO_ARRANQUE_MS=10000
until curl -s -o /dev/null "http://localhost:48083/salud/vivo"; do sleep 0.2; done
if salida=$(mvn_suite -Dbitacora.url=http://localhost:48083 -Dbitacora.esperar=no '-Detiquetas=@arranque'); then
  ok "mientras arranca: vivo 200, listo 503 y la API 503 ($(resumen <<<"$salida"))"
else
  falla "el escenario de arranque"; echo "$salida" | tail -20
fi
lanzada=$(( $(date +%s) - inicio ))
if salida=$(mvn_suite -Dbitacora.url=http://localhost:48083); then
  ok "lanzada a los $lanzada s de un arranque de 10 s, la suite esperó a /salud/listo y pasó: $(resumen <<<"$salida")"
else
  falla "la suite contra el arranque lento"; echo "$salida" | tail -20
fi

[ "$fallos" -eq 0 ]
