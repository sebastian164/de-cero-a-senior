# Funciones comunes de las pruebas de esta solución de referencia (Docker).
# Lo carga probar.sh. No hace falta ejecutarlo por separado.
#
# Variables que puedes cambiar al invocar probar.sh:
#   PREFIJO   prefijo de contenedores, redes, volúmenes e imágenes que crea la prueba (por defecto: ref)
#   MANTENER  1 = no borrar las imágenes bitacora-* al terminar (acelera una segunda ejecución)

set -uo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Raíz del curso (la carpeta docker-kubernetes): esta carpeta está en <raíz>/<docker|kubernetes>/<sesión>/taller/solucion-de-referencia
CURSO="$(cd "$AQUI/../../../.." && pwd)"
APP="$CURSO/app"
PREFIJO="${PREFIJO:-ref}"
SALIDAS="$(mktemp -d "${TMPDIR:-/tmp}/${PREFIJO}-salidas.XXXXXX")"
FALLOS=${FALLOS:-0}

for herramienta in docker curl python3; do
  command -v "$herramienta" >/dev/null || { echo "Falta '$herramienta': instálalo antes de ejecutar esta prueba (ver README.md)." >&2; exit 2; }
done
docker info >/dev/null 2>&1 || { echo "Docker no responde: arranca Docker Desktop (o el servicio de Docker) y vuelve a intentarlo." >&2; exit 2; }

titulo() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
nota()   { printf '   %s\n' "$*"; }
ok()     { printf '   \033[32mOK\033[0m  %s\n' "$*"; }
mal()    { printf '   \033[31mFALLA\033[0m %s\n' "$*"; FALLOS=$((FALLOS + 1)); }

# afirmar "descripción" orden...  -> OK si la orden termina en 0
afirmar() { local d="$1"; shift; if "$@" >/dev/null 2>&1; then ok "$d"; else mal "$d"; fi; }
# negar "descripción" orden...  -> OK si la orden falla
negar() { local d="$1"; shift; if "$@" >/dev/null 2>&1; then mal "$d"; else ok "$d"; fi; }
# igual "descripción" esperado obtenido
igual() { if [ "$2" = "$3" ]; then ok "$1 ($3)"; else mal "$1: se esperaba '$2' y se obtuvo '$3'"; fi; }

# esperar_hasta segundos orden...  -> reintenta cada segundo
esperar_hasta() {
  local limite=$1 i=0; shift
  until "$@" >/dev/null 2>&1; do i=$((i + 1)); [ $i -ge "$limite" ] && return 1; sleep 1; done
}
ahora_ms() { python3 -c 'import time; print(int(time.time()*1000))'; }
codigo_http() { curl -s -o /dev/null -m 5 -w '%{http_code}' "$@" || true; }
# sed -i que funciona igual en macOS y en Linux
sed_i() { local e="$1"; shift; sed -e "$e" "$1" > "$1.tmp" && mv "$1.tmp" "$1"; }

# Hace una copia de trabajo de esta carpeta (sin guiones ni documentos) y le añade el código de la
# aplicación de práctica (docker-kubernetes/app), que no se duplica en el repositorio.
#   montar_solucion <destino> [subcarpeta-de-la-app] [origen]
montar_solucion() {
  local destino="$1" app="${2:-.}" origen="${3:-$AQUI}"
  mkdir -p "$destino"
  local excluir=(--exclude .env)
  [ "$origen" = "$AQUI" ] && excluir+=(--exclude ./probar.sh --exclude ./lib.sh --exclude ./bajar.sh --exclude ./README.md --exclude ./ANALISIS.md)
  (cd "$origen" && tar "${excluir[@]}" -cf - .) | (cd "$destino" && tar -xf -)
  sobreponer_app "$destino/$app"
}
sobreponer_app() { # completa <dir>/api y <dir>/web con el código de la aplicación de práctica
  local d="$1"
  [ -d "$d/api" ] && cp "$APP/api/Servidor.java" "$d/api/"
  [ -d "$d/web" ] && cp "$APP/web/index.html" "$APP/web/default.conf.template" "$d/web/"
  return 0
}

# Imágenes de Bitácora de las sesiones 02 y 03 (sus Dockerfiles de referencia + el código de la aplicación).
#   construir_bitacora <api-0.1.0|api-1.0.0|web-0.1.0>  -> deja $PREFIJO-bitacora-<...> construida
SESION02="$CURSO/docker/02-tu-primera-imagen/taller/solucion-de-referencia"
SESION03="$CURSO/docker/03-imagenes-eficientes-y-seguras/taller/solucion-de-referencia"
construir_bitacora() {
  local que="$1" ctx; ctx="$(mktemp -d "${TMPDIR:-/tmp}/${PREFIJO}-ctx.XXXXXX")"
  case "$que" in
    api-0.1.0) cp "$SESION02/api/"{Dockerfile,.dockerignore} "$ctx/"; sobreponer_app_api "$ctx" ;;
    api-1.0.0) cp "$SESION03/api/"{Dockerfile,.dockerignore} "$ctx/"; sobreponer_app_api "$ctx" ;;
    web-0.1.0) cp "$SESION02/web/"{Dockerfile,.dockerignore} "$ctx/"; cp "$APP/web/index.html" "$APP/web/default.conf.template" "$ctx/" ;;
  esac
  docker build -q -t "$PREFIJO-bitacora-${que%-*}:${que#*-}" "$ctx" >/dev/null || { mal "no se pudo construir bitacora-$que"; rm -rf "$ctx"; return 1; }
  rm -rf "$ctx"
}
sobreponer_app_api() { cp "$APP/api/Servidor.java" "$1/"; }
asegurar_bitacora() { docker image inspect "$PREFIJO-bitacora-${1%-*}:${1#*-}" >/dev/null 2>&1 || construir_bitacora "$1"; }

# Borra las imágenes bitacora-* de la prueba (salvo que MANTENER=1) y la carpeta de salidas.
limpiar_imagenes() {
  [ "${MANTENER:-0}" = 1 ] && return 0
  docker images --format '{{.Repository}}:{{.Tag}}' | grep "^$PREFIJO-" | xargs docker rmi -f >/dev/null 2>&1 || true
  # imágenes del registro local de la sesión 06 (localhost:50900/bitacora-*)
  docker images --format '{{.Repository}}:{{.Tag}}' | grep "^localhost:50900/bitacora-" | xargs docker rmi -f >/dev/null 2>&1 || true
}
trap 'rm -rf "$SALIDAS"' EXIT

# Baja todo lo que crea la prueba: contenedores, redes, volúmenes, el builder de buildx y las imágenes (todo con el prefijo $PREFIJO-).
bajar_todo() {
  docker ps -aq --filter "name=$PREFIJO-" | xargs docker rm -fv >/dev/null 2>&1 || true
  docker network ls -q --filter "name=$PREFIJO-" | xargs docker network rm >/dev/null 2>&1 || true
  docker volume ls -q --filter "name=$PREFIJO-" | xargs docker volume rm >/dev/null 2>&1 || true
  docker buildx rm "$PREFIJO-d06-builder" >/dev/null 2>&1 || true
  MANTENER=0 limpiar_imagenes
}

terminar() {
  limpiar_imagenes
  if [ "$FALLOS" -eq 0 ]; then printf '\n\033[32m%s: todo correcto\033[0m\n' "${1:-prueba}"; exit 0; fi
  printf '\n\033[31m%s: %d fallos\033[0m\n' "${1:-prueba}" "$FALLOS"; exit 1
}
