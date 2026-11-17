#!/usr/bin/env bash
# Solución de referencia del taller Karate 03. Comprueba los criterios (puertos 48080-48099).
#   1-3  paralelo con 1, 2, 4 y 8 hilos, y cinco ejecuciones seguidas en paralelo
#   4-5  simulador con 503, lento y roto; la web de nginx apuntando al simulador
#   6-7  el mismo contrato contra la API real y el simulador; un cambio incompatible lo detecta el contrato
#   8-9  flujo de CI: actionlint, el flujo ejecutado con act (push, pull_request y una API con un defecto que lo hace fallar)
#   10   prueba de carga reutilizando info.feature
# Variables: CORRIDAS (5), con RAPIDO=1 las corridas se reducen a 3.
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
proyecto="pub-practicas-karate03"
SIM_PID=""
trabajo_ci=$(mktemp -d)
restos_de_act() { # act conserva el contenedor y los volúmenes si el trabajo falla; se borra lo que quede
  docker ps -aq --filter name=act-karate-api | xargs docker rm -f >/dev/null 2>&1 || true
  docker volume ls -q --filter name=act-karate-api | xargs docker volume rm >/dev/null 2>&1 || true
}
limpiar() {
  [ -n "$SIM_PID" ] && kill "$SIM_PID" 2>/dev/null || true
  parar_todas_las_bitacoras
  rm -rf "$BITACORA_TRABAJO" "$aqui/target" "$trabajo_ci"
  restos_de_act
  (cd "$aqui/ci" && docker compose -p "$proyecto-web" -f web-compose.yaml down -v >/dev/null 2>&1) || true
  (cd "$aqui/ci" && CLAVE_CI=x docker compose -p "$proyecto-ci" -f compose.yaml down -v --rmi local >/dev/null 2>&1) || true
}
trap limpiar EXIT
CORRIDAS=${CORRIDAS:-5}; [ "${RAPIDO:-0}" = 1 ] && CORRIDAS=3  # la señal que detecta dependencias de datos son las corridas contra API vacía; estas con datos solo confirman estabilidad
fallos=0
ok() { echo "  ok     $*"; }
falla() { echo "  FALLA  $*"; fallos=$((fallos + 1)); }
resumen() { grep -E '^ESCENARIOS' || echo "(sin resumen)"; }
CLAVE="k3-$RANDOM-$RANDOM"
cd "$aqui"
mvn -q dependency:build-classpath -Dmdep.outputFile="$trabajo_ci/classpath.txt" >/dev/null
mvn -q test-compile >/dev/null
CP="$aqui/target/test-classes:$(cat "$trabajo_ci/classpath.txt")"

iniciar_bitacora k03 48080 CLAVE_API="$CLAVE"
esperar_lista 48080
URL=http://localhost:48080

echo "== Pasos 1 a 3: paralelo"
echo "  hilos | ms de la suite (dentro de Karate) | ms con Maven"
for h in 1 2 4 8; do
  t0=$(date +%s%N)
  salida=$(mvn -q test -Dbitacora.clave="$CLAVE" -Dhilos=$h 2>&1) || { falla "con $h hilos"; echo "$salida" | tail -30; continue; }
  total=$(( ($(date +%s%N) - t0) / 1000000 ))
  echo "  $h | $(grep -oE 'EN [0-9]+ ms' <<<"$salida" | grep -oE '[0-9]+') | $total  ($(resumen <<<"$salida"))"
done
echo "  los mismos hilos, sin los escenarios @lento (los que usan /api/carga):"
for h in 1 2 4 8; do
  salida=$(mvn -q test -Dbitacora.clave="$CLAVE" -Dhilos=$h '-Detiquetas=~@arranque;~@falla;~@lento' 2>&1) || { falla "sin @lento con $h hilos"; echo "$salida" | tail -30; continue; }
  echo "  $h | $(grep -oE 'EN [0-9]+ ms' <<<"$salida" | grep -oE '[0-9]+') | ($(resumen <<<"$salida"))"
done
echo "  solo @lento, con 1 y con 8 hilos:"
for h in 1 8; do
  salida=$(mvn -q test -Dbitacora.clave="$CLAVE" -Dhilos=$h -Detiquetas=@lento 2>&1) || { falla "solo @lento con $h hilos"; continue; }
  echo "  $h | $(grep -oE 'EN [0-9]+ ms' <<<"$salida" | grep -oE '[0-9]+') | ($(resumen <<<"$salida"))"
done
pasaron=0
for i in $(seq 1 "$CORRIDAS"); do
  if salida=$(mvn -q test -Dbitacora.clave="$CLAVE" -Dhilos=4 2>&1); then pasaron=$((pasaron + 1)); else echo "$salida" | tail -25; fi
done
[ "$pasaron" -eq "$CORRIDAS" ] && ok "la suite pasó $pasaron de $CORRIDAS veces seguidas con 4 hilos" || falla "la suite pasó $pasaron de $CORRIDAS veces"

# Independencia de los datos (lo que pide el paso 2 de verdad): nada puede depender de que otro escenario haya guardado antes.
# a) cada .feature ejecutado solo contra una API recién arrancada y vacía; b) varias corridas con 4 hilos, cada una contra una API vacía.
echo "  independencia: cada .feature solo, contra una API vacía"
for f in "$aqui"/src/test/java/bitacora/*.feature; do
  n=$(basename "$f")
  case "$n" in arranque.feature|falla-a-proposito.feature) continue ;; esac   # el filtro de etiquetas los deja sin escenarios: son @arranque y @falla a propósito
  iniciar_bitacora k03-vacia 48085 CLAVE_API="$CLAVE"; esperar_lista 48085
  if [ "$(curl -s localhost:48085/api/notas | grep -c '"notas":\[\]')" != 1 ]; then falla "la API de $n no estaba vacía"; fi
  if salida=$(mvn -q test -Dbitacora.clave="$CLAVE" -Dbitacora.url=http://localhost:48085 -Druta="classpath:bitacora/$n" -Detiquetas='~@arranque;~@falla' 2>&1); then
    echo "    ok  $n: $(resumen <<<"$salida")"
  else falla "$n solo, contra una API vacía"; echo "$salida" | tail -20; fi
  parar_bitacora k03-vacia
done
vacias=0; CORRIDAS_VACIAS=${CORRIDAS_VACIAS:-10}; [ "${RAPIDO:-0}" = 1 ] && CORRIDAS_VACIAS=2
for i in $(seq 1 "$CORRIDAS_VACIAS"); do
  iniciar_bitacora k03-vacia 48085 CLAVE_API="$CLAVE"; esperar_lista 48085
  if salida=$(mvn -q test -Dbitacora.clave="$CLAVE" -Dbitacora.url=http://localhost:48085 -Dhilos=4 '-Detiquetas=~@arranque;~@falla;~@lento' 2>&1); then vacias=$((vacias + 1)); else echo "$salida" | tail -25; fi
  parar_bitacora k03-vacia
done
[ "$vacias" -eq "$CORRIDAS_VACIAS" ] && ok "la suite (sin @lento) pasó $vacias de $CORRIDAS_VACIAS veces con 4 hilos, cada una contra una API vacía" || falla "contra una API vacía pasó $vacias de $CORRIDAS_VACIAS"

echo "== Pasos 4 y 6: simulador y contrato"
java -cp "$CP" Simulador 48090 >"$trabajo_ci/simulador.log" 2>&1 &
SIM_PID=$!
for _ in $(seq 1 50); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:48090/salud/listo)" = 200 ] && break; sleep 0.2; done
for destino in "API real|$URL" "simulador|http://localhost:48090"; do
  if salida=$(mvn -q test -Dbitacora.clave="$CLAVE" -Dbitacora.url="${destino#*|}" -Druta=classpath:contrato 2>&1); then ok "contrato contra ${destino%|*}: $(resumen <<<"$salida")"; else falla "contrato contra ${destino%|*}"; echo "$salida" | tail -20; fi
done
for modo in 503 lento roto; do
  curl -s -X PUT localhost:48090/control/modo -d "$modo" >/dev/null
  echo "  modo $modo: $(curl -s -m 10 -o /dev/null -w 'HTTP %{http_code} en %{time_total}s, %{content_type}' localhost:48090/api/info)"
done
curl -s -X PUT localhost:48090/control/modo -d normal >/dev/null

echo "== Paso 5: la web de nginx apuntando al simulador"
(cd "$aqui/ci" && docker compose -p "$proyecto-web" -f web-compose.yaml up -d --wait >/dev/null 2>&1) || falla "no arrancó el nginx"
for modo in normal 503 lento roto; do
  curl -s -X PUT localhost:48090/control/modo -d "$modo" >/dev/null
  echo "  -- simulador en modo $modo (la página, vista a través de nginx en :48082)"
  node "$aqui/ci/pagina.mjs" http://localhost:48082 "$(dirname "$BITACORA_ORIGINAL")/../web/index.html" | sed 's/^/     /'
done
(cd "$aqui/ci" && docker compose -p "$proyecto-web" -f web-compose.yaml down -v >/dev/null 2>&1)
kill "$SIM_PID"; SIM_PID=""

echo "== Paso 7: un cambio incompatible en la API real"
copia=$(mktemp -d)
sed 's/"\\"visitas\\":"/"\\"visits\\":"/' "$BITACORA_ORIGINAL" >"$copia/Servidor.java"
BITACORA_FUENTE="$copia/Servidor.java" iniciar_bitacora k03-cambio 48083 CLAVE_API="$CLAVE"
esperar_lista 48083
# a) el contrato contra la API cambiada falla: avisa de que el cliente (y el simulador) ya no coinciden con la real.
if salida=$(mvn -q test -Dbitacora.clave="$CLAVE" -Dbitacora.url=http://localhost:48083 -Druta=classpath:contrato 2>&1); then falla "el contrato debía fallar contra la API cambiada"; else ok "contrato contra la API cambiada: falla ($(grep -oE 'FALLAN [0-9]+' <<<"$salida"))"; fi
# b) se actualiza esquemas.js al campo nuevo: ahora el contrato pasa contra la real y falla contra el simulador, que quedó desactualizado.
cp -r "$aqui" "$copia/proyecto"; rm -rf "$copia/proyecto/target"
sed -i '' 's/      visitas: /      visits: /' "$copia/proyecto/src/test/java/esquemas.js"
(cd "$copia/proyecto" && mvn -q test-compile >/dev/null)
CP2="$copia/proyecto/target/test-classes:$(cat "$trabajo_ci/classpath.txt")"
java -cp "$CP2" Simulador 48090 >/dev/null 2>&1 &
SIM_PID=$!
for _ in $(seq 1 50); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:48090/salud/listo)" = 200 ] && break; sleep 0.2; done
if (cd "$copia/proyecto" && mvn -q test -Dbitacora.clave="$CLAVE" -Dbitacora.url=http://localhost:48083 -Druta=classpath:contrato >/dev/null 2>&1); then ok "con el esquema nuevo, la API cambiada cumple el contrato"; else falla "con el esquema nuevo la API cambiada debía cumplirlo"; fi
if salida=$(cd "$copia/proyecto" && mvn -q test -Dbitacora.clave="$CLAVE" -Dbitacora.url=http://localhost:48090 -Druta=classpath:contrato 2>&1); then falla "el simulador desactualizado debía fallar el contrato"; else ok "con el esquema nuevo, el simulador desactualizado falla el contrato: $(grep -oE 'FALLAN [0-9]+' <<<"$salida")"; fi
kill "$SIM_PID"; SIM_PID=""
parar_bitacora k03-cambio

echo "== Pasos 8 y 9: integración continua (el flujo de GitHub Actions, ejecutado con act)"
if ! command -v act >/dev/null || ! command -v actionlint >/dev/null; then
  echo "  omitido: instala act y actionlint para ejecutar el flujo de GitHub Actions (CON_ACT=1 lo exige)"
  [ "${CON_ACT:-0}" = 1 ] && falla "faltan act o actionlint"
else
  if actionlint "$aqui/.github/workflows/karate.yml"; then ok "actionlint no encuentra errores en karate.yml"; else falla "actionlint"; fi
  # Raíz del repositorio de CI = esta carpeta + api/Servidor.java (la API que se prueba). act ejecuta el flujo tal cual, en un contenedor
  # con la imagen de act-24.04 más Maven (la imagen de GitHub trae Maven; la de act no) y con el Docker del anfitrión.
  ACT_IMAGEN=pub-practicas-act:1
  if ! docker image inspect "$ACT_IMAGEN" >/dev/null 2>&1; then
    mkdir -p "$trabajo_ci/imagen"; printf 'FROM catthehacker/ubuntu:act-24.04\nRUN apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/*\n' >"$trabajo_ci/imagen/Dockerfile"
    docker build -q -t "$ACT_IMAGEN" "$trabajo_ci/imagen" >/dev/null
  fi
  CLAVE_CI="ci-$RANDOM-$RANDOM"
  repo_ci() { # repo_ci <carpeta> <Servidor.java de la API>
    rm -rf "$1"; mkdir -p "$1/api"
    rsync -a --exclude target --exclude '*.log' --exclude ANALISIS.md --exclude README.md --exclude comprobar.sh "$aqui/" "$1/"
    cp "$2" "$1/api/Servidor.java"
    (cd "$1" && git init -q . && git add -A && git -c user.email=ci@example.com -c user.name=ci commit -qm "repositorio de CI")
  }
  correr_act() { # correr_act <carpeta> <evento> <registro>   (sale con el código de act)
    (cd "$1" && rm -rf .artefactos && act "$2" --rm -P ubuntu-24.04="$ACT_IMAGEN" --pull=false --var PUERTO_CI=48081 \
       --env COMPOSE_PROJECT_NAME=pub-practicas-karate03-act -s CLAVE_CI="$CLAVE_CI" --artifact-server-path "$1/.artefactos") >"$3" 2>&1
  }
  repo_ci "$trabajo_ci/ok" "$BITACORA_ORIGINAL"
  if correr_act "$trabajo_ci/ok" push "$trabajo_ci/push.log"; then
    ok "act push: el flujo termina en verde; $(grep -oE 'ESCENARIOS [0-9]+ PASAN [0-9]+ FALLAN [0-9]+' "$trabajo_ci/push.log" | head -1) (solo humo)"
    grep -q 'ESCENARIOS 5 PASAN 5 ' "$trabajo_ci/push.log" && ok "en push corrieron los cinco escenarios de humo, no la suite completa" || falla "en push debían ser cinco escenarios"
  else falla "act push falló"; tail -30 "$trabajo_ci/push.log"; fi
  if correr_act "$trabajo_ci/ok" pull_request "$trabajo_ci/pr.log"; then
    ok "act pull_request: el flujo termina en verde; $(grep -oE 'ESCENARIOS [0-9]+ PASAN [0-9]+ FALLAN [0-9]+' "$trabajo_ci/pr.log" | head -1) (suite completa)"
    grep -q 'ESCENARIOS 36 PASAN 36 ' "$trabajo_ci/pr.log" || falla "en pull_request debían correr los 36 escenarios"
    contenido=$(unzip -l "$trabajo_ci/ok/.artefactos/"*/informe-karate/informe-karate.zip 2>/dev/null || true)   # a una variable: grep -q cerraría la tubería antes de tiempo con pipefail
    grep -q 'Success - Main Publicar el informe' "$trabajo_ci/pr.log" && grep -q karate-summary.html <<<"$contenido" \
      && ok "el informe se publicó como artefacto (informe-karate.zip con karate-summary.html)" || falla "no se publicó el informe"
  else falla "act pull_request falló"; tail -30 "$trabajo_ci/pr.log"; fi

  # Paso 9: la rama con un defecto. La API devuelve 200 en vez de 201 al guardar una nota.
  sed 's/responder(peticion, 201, "application\/json", "{\\"guardada\\"/responder(peticion, 200, "application\/json", "{\\"guardada\\"/' "$BITACORA_ORIGINAL" >"$trabajo_ci/Defectuosa.java"
  grep -q 'responder(peticion, 200, "application/json", "{\\"guardada' "$trabajo_ci/Defectuosa.java" || falla "no se pudo introducir el defecto"
  repo_ci "$trabajo_ci/mal" "$trabajo_ci/Defectuosa.java"
  if correr_act "$trabajo_ci/mal" push "$trabajo_ci/mal.log"; then
    falla "con el defecto el flujo debía fallar"
  else
    ok "act push con el defecto: el flujo falla (Job failed): $(grep -oE 'FALLAN [0-9]+' "$trabajo_ci/mal.log" | head -1)"
    echo "  -- lo que dice el registro del flujo:"
    grep -E '^\[karate/api\]   \| +(ESCENARIOS|- una nota|.*status code|.*expected|match failed)' "$trabajo_ci/mal.log" | head -8 | sed 's/^/     /'
    grep -q 'Success - Main Publicar el informe' "$trabajo_ci/mal.log" && ok "el paso del informe se ejecutó aunque la suite falló (if: always())" || falla "el informe no se publicó tras el fallo"
    (cd "$trabajo_ci/mal/.artefactos" && mkdir -p x && for z in */informe-karate/informe-karate.zip; do unzip -qo "$z" -d x; done)
    if grep -rq 'una nota guardada aparece en la lista' "$trabajo_ci/mal/.artefactos/x"; then
      ok "el informe publicado nombra el escenario fallido: \"una nota guardada aparece en la lista\""
    else falla "el informe no nombra el escenario"; fi
  fi

fi

echo "== Paso 10: prueba de carga con info.feature"
for u in 1 8; do
  salida=$(mvn -q test -Dtest=CargaTest -Dcarga=true -Dusuarios=$u -Diteraciones=20 -Dbitacora.clave="$CLAVE" -Dbitacora.esperar=no 2>&1) || { falla "carga con $u usuarios"; echo "$salida" | tail -20; continue; }
  grep -E '^CARGA' <<<"$salida" | sed 's/^/  /'
done
parar_todas_las_bitacoras
rm -rf "$copia"
[ "$fallos" -eq 0 ]
