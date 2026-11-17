#!/usr/bin/env bash
# Solución de referencia del taller Karate 02. Comprueba los criterios.
#   - Ningún secreto en el proyecto (paso 5) y sin clave la suite falla con un mensaje claro.
#   - La misma suite pasa en dos ambientes cambiando solo karate.env (paso 6):
#       local       Bitácora con java, puerto 48080, una clave
#       contenedor  Bitácora con Compose (compose.yaml), puerto 48081, otra clave
#   - Solo @humo, con su tiempo (paso 7), y solo @lento.
#   - Un campo renombrado en una copia de Servidor.java: cuántos escenarios fallan, y que se arregla en un solo sitio (paso 2).
#   - El mensaje de match each con una nota que incumple la regla (paso 8).
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
proyecto="pub-practicas-karate02"
limpiar() {
  parar_todas_las_bitacoras
  rm -rf "$BITACORA_TRABAJO" "$aqui/target"
  (cd "$aqui" && docker compose -p "$proyecto" down -v --rmi local >/dev/null 2>&1) || true
}
trap limpiar EXIT
cd "$aqui"
CLAVE_LOCAL="local-$RANDOM-$RANDOM"
export CLAVE_CONTENEDOR="contenedor-$RANDOM-$RANDOM"
fallos=0
ok() { echo "  ok     $*"; }
falla() { echo "  FALLA  $*"; fallos=$((fallos + 1)); }
resumen() { grep -E '^ESCENARIOS' || echo "(sin resumen)"; }
nombres() { sed -n '/failed features/,$p' | grep -E '^    - ' | sed 's/^    - //' | paste -sd ';' -; }

# Paso 5: sin secretos. Ni las claves de esta ejecución ni la del curso ("secreta") aparecen en el proyecto.
if grep -rnE "secreta|$CLAVE_LOCAL|$CLAVE_CONTENEDOR|https?://[a-z]" --include='*.feature' --include='*.json' src/test/java; then
  falla "hay direcciones o claves en los .feature o en los datos"
else
  ok "ningún .feature ni archivo de datos tiene direcciones ni claves"
fi
if grep -rn "secreta" src/test/java compose.yaml; then falla "la clave del curso aparece en el proyecto"; else ok "ninguna clave en el proyecto ni en compose.yaml"; fi

# Ambiente local.
iniciar_bitacora karate02 48080 CLAVE_API="$CLAVE_LOCAL"
esperar_lista 48080
# Antes de nada, con la API recién arrancada (lista de notas vacía): el escenario de la ruta por prefijo no puede depender de notas ajenas.
if salida=$(mvn -q test -Dbitacora.clave="$CLAVE_LOCAL" '-Descenario=una ruta que empieza como una conocida responde como la conocida' 2>&1); then ok "con la API vacía, el escenario de la ruta por prefijo pasa solo: $(resumen <<<"$salida")"; else falla "el escenario de la ruta por prefijo depende de datos ajenos"; echo "$salida" | tail -20; fi
if salida=$(mvn -q test -Dbitacora.clave="$CLAVE_LOCAL" 2>&1); then ok "ambiente local: $(resumen <<<"$salida")"; else falla "ambiente local"; echo "$salida" | tail -40; fi

if salida=$(mvn -q test 2>&1); then
  falla "sin clave la suite debía fallar"
elif grep -q 'Falta la clave de la API: ejecuta con -Dbitacora.clave=<clave>' <<<"$salida"; then
  ok "sin clave falla con: Falta la clave de la API: ejecuta con -Dbitacora.clave=<clave>"
else
  falla "sin clave falla, pero sin el mensaje claro"
fi

inicio=$(date +%s%N)
if salida=$(mvn -q test -Dbitacora.clave="$CLAVE_LOCAL" -Detiquetas=@humo 2>&1); then
  ok "solo @humo: $(resumen <<<"$salida"); con Maven y JVM, $(( ($(date +%s%N) - inicio) / 1000000 )) ms"
  grep -q '^ESCENARIOS 5 ' <<<"$salida" || falla "@humo debían ser exactamente cinco escenarios"
else
  falla "@humo"
fi
if salida=$(mvn -q test -Dbitacora.clave="$CLAVE_LOCAL" -Detiquetas=@lento 2>&1); then ok "solo @lento: $(resumen <<<"$salida")"; else falla "@lento"; fi

# Paso 8: una nota de dos caracteres incumple "al menos tres": el mensaje de match each dice cuál y por qué.
if salida=$(mvn -q test -Dbitacora.clave="$CLAVE_LOCAL" -Detiquetas=@falla 2>&1); then
  falla "la regla más estricta que la API debía fallar"
elif grep -q 'match each failed' <<<"$salida" && grep -q "_.length >= 3" <<<"$salida"; then
  ok "match each falla y nombra la regla: $(grep -m1 -oE 'match each failed[^,]*' <<<"$salida")"
  grep -m3 -E '\$\[[0-9]+\]|actual|expected' <<<"$salida" | sed 's/^/           /' || true
else
  falla "match each falló sin un mensaje útil"; echo "$salida" | tail -30
fi
parar_bitacora karate02

# Ambiente contenedor: misma suite, solo cambia karate.env (y la clave, que llega por parámetro).
(cd "$aqui" && docker compose -p "$proyecto" up -d --build --wait >/dev/null 2>&1) || { falla "no se levantó el contenedor"; }
if salida=$(mvn -q test -Dkarate.env=contenedor -Dbitacora.clave="$CLAVE_CONTENEDOR" 2>&1); then
  ok "ambiente contenedor: $(resumen <<<"$salida")"
else
  falla "ambiente contenedor"; echo "$salida" | tail -40
fi
if mvn -q test -Dkarate.env=contenedor -Dbitacora.clave="$CLAVE_LOCAL" >/dev/null 2>&1; then
  falla "con la clave del otro ambiente debía fallar"
else
  ok "con la clave del ambiente local, el contenedor rechaza: cada ambiente tiene la suya"
fi
if salida=$(mvn -q test -Dkarate.env=desconocido -Dbitacora.clave=x 2>&1); then falla "un ambiente desconocido debía fallar"; else
  grep -q 'Ambiente desconocido: desconocido' <<<"$salida" && ok "un ambiente desconocido falla con un mensaje claro" || falla "ambiente desconocido sin mensaje"
fi
(cd "$aqui" && docker compose -p "$proyecto" down -v --rmi local >/dev/null 2>&1) || true

# Paso 2: "visitas" pasa a llamarse "visits" en una copia de Servidor.java.
copia=$(mktemp -d)
sed 's/"\\"visitas\\":"/"\\"visits\\":"/' "$BITACORA_ORIGINAL" >"$copia/Servidor.java"
grep -q 'visits' "$copia/Servidor.java" || { falla "no se pudo renombrar el campo"; }
BITACORA_FUENTE="$copia/Servidor.java" iniciar_bitacora karate02-renombrado 48080 CLAVE_API="$CLAVE_LOCAL"
esperar_lista 48080
salida=$(mvn -q test -Dbitacora.clave="$CLAVE_LOCAL" 2>&1) || true
fallan=$(grep -oE 'FALLAN [0-9]+' <<<"$salida" | grep -oE '[0-9]+' || echo "?")
ok "con visitas -> visits fallan $fallan escenarios: $(nombres <<<"$salida")"
# El arreglo es una línea, en un solo archivo: esquemas.js.
cp -r "$aqui" "$copia/proyecto"
rm -rf "$copia/proyecto/target"
sed -i '' 's/      visitas: /      visits: /' "$copia/proyecto/src/test/java/esquemas.js"
cambiados=$(diff -r "$aqui/src" "$copia/proyecto/src" | grep -c '^diff' || true)
cambiados=${cambiados:-0}
if salida=$(cd "$copia/proyecto" && mvn -q test -Dbitacora.clave="$CLAVE_LOCAL" 2>&1); then
  # Queda un escenario que usa response.visitas por su nombre: "cada consulta suma al menos una visita".
  ok "corrigiendo solo esquemas.js ($((cambiados + 1)) archivo) la suite vuelve a pasar: $(resumen <<<"$salida")"
else
  quedan=$(grep -oE 'FALLAN [0-9]+' <<<"$salida" | grep -oE '[0-9]+' || echo "?")
  ok "corrigiendo solo esquemas.js quedan $quedan escenarios que usan el campo por su nombre: $(nombres <<<"$salida")"
fi
parar_bitacora karate02-renombrado
rm -rf "$copia"

[ "$fallos" -eq 0 ]
