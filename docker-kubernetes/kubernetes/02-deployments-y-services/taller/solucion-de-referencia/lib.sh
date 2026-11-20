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

# ---------------------------------------------------------------------------------------------------------
# Kubernetes (clúster kind local)
#   CLUSTER          nombre del clúster que crea la prueba (por defecto: referencia). Es distinto del clúster
#                    "curso" del taller para no tocar el tuyo.
#   PUERTO_ENTRADA   puerto del equipo que llega al NodePort 30080 del Gateway (por defecto: 50080)
#   MANTENER         1 = no bajar el clúster al terminar (útil para encadenar varias pruebas)
# ---------------------------------------------------------------------------------------------------------
CLUSTER="${CLUSTER:-referencia}"
PUERTO_ENTRADA="${PUERTO_ENTRADA:-50080}"
# Un archivo de configuración propio: la prueba no modifica tu ~/.kube/config.
export KUBECONFIG="${TMPDIR:-/tmp}/kubeconfig-$CLUSTER"
for herramienta in kind kubectl; do
  command -v "$herramienta" >/dev/null || { echo "Falta '$herramienta': instálalo antes de ejecutar esta prueba (ver README.md)." >&2; exit 2; }
done
METRICS_SERVER=v0.9.1
ENVOY_GATEWAY=v1.9.3

k() { kubectl --context "kind-$CLUSTER" "$@"; }

nombre_completo() { case "$1" in */*/*) echo "$1";; */*) echo "docker.io/$1";; *) echo "docker.io/library/$1";; esac; }

# Carga una imagen local en los nodos con el nombre que usan los manifiestos del curso.
#   cargar_imagen <imagen-local> [nombre-en-el-cluster]
cargar_imagen() {
  local local_="$1" destino="${2:-}" archivo
  archivo="$(mktemp "${TMPDIR:-/tmp}/${PREFIJO}-imagen.XXXXXX")"
  docker save --platform "linux/$(docker version --format '{{.Server.Arch}}')" -o "$archivo" "$local_"
  kind load image-archive --name "$CLUSTER" "$archivo" >/dev/null
  rm -f "$archivo"
  [ -z "$destino" ] && return 0
  for nodo in $(kind get nodes --name "$CLUSTER"); do
    docker exec "$nodo" ctr -n k8s.io images tag --force "docker.io/library/$local_" "$(nombre_completo "$destino")" >/dev/null
  done
}

# Crea el clúster (un plano de control y dos nodos de trabajo) si no existe y carga las imágenes de Bitácora.
preparar_cluster() {
  if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    titulo "Creando el clúster $CLUSTER (kind $(kind version | awk '{print $2}'))"
    sed "s/hostPort: 50080/hostPort: $PUERTO_ENTRADA/" "$AQUI/kind.yaml" \
      | kind create cluster --name "$CLUSTER" --config - --kubeconfig "$KUBECONFIG" --wait 180s \
      || { echo "No se pudo crear el clúster $CLUSTER." >&2; exit 2; }
  fi
  [ -s "$KUBECONFIG" ] || kind export kubeconfig --name "$CLUSTER" --kubeconfig "$KUBECONFIG"
  local cp; cp="$(kind get nodes --name "$CLUSTER" | grep control-plane)"
  if ! docker exec "$cp" test -f /tmp/imagenes-cargadas; then
    titulo "Cargando las imágenes en los nodos"
    asegurar_bitacora api-1.0.0; asegurar_bitacora web-0.1.0
    docker tag "$PREFIJO-bitacora-api:1.0.0" "$PREFIJO-bitacora-api:1.0.1"   # taller 02, paso 8: la misma imagen con otra etiqueta
    cargar_imagen "$PREFIJO-bitacora-api:1.0.0" bitacora-api:1.0.0
    cargar_imagen "$PREFIJO-bitacora-api:1.0.1" bitacora-api:1.0.1
    cargar_imagen "$PREFIJO-bitacora-web:0.1.0" bitacora-web:0.1.0
    local img
    for img in curlimages/curl:8.17.0 busybox:1.37 postgres:17; do
      docker image inspect "$img" >/dev/null 2>&1 || docker pull -q "$img" >/dev/null
      cargar_imagen "$img"
    done
    docker exec "$cp" touch /tmp/imagenes-cargadas
  fi
  k get nodes
}

instalar_metrics_server() {
  k -n kube-system get deploy metrics-server >/dev/null 2>&1 && return 0
  titulo "metrics-server $METRICS_SERVER"
  k apply -f "https://github.com/kubernetes-sigs/metrics-server/releases/download/$METRICS_SERVER/components.yaml" >/dev/null
  # kind firma los certificados de los kubelet con su propia CA: metrics-server no puede verificarlos.
  # En un clúster local se le pide que no los verifique.
  k -n kube-system patch deployment metrics-server --type=json \
    -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' >/dev/null
  k -n kube-system rollout status deploy/metrics-server --timeout=180s
  # la API de métricas tarda en tener datos después del despliegue
  esperar_hasta 180 k top nodes || echo "metrics-server no entrega métricas todavía" >&2
}

instalar_envoy_gateway() {
  command -v helm >/dev/null || { echo "Falta 'helm' (ver README.md)." >&2; exit 2; }
  if ! k -n envoy-gateway-system get deploy envoy-gateway >/dev/null 2>&1; then
    titulo "Envoy Gateway $ENVOY_GATEWAY"
    helm install eg oci://docker.io/envoyproxy/gateway-helm --version "$ENVOY_GATEWAY" \
      -n envoy-gateway-system --create-namespace --kube-context "kind-$CLUSTER" --kubeconfig "$KUBECONFIG" --wait --timeout 5m >/dev/null
  fi
  k -n envoy-gateway-system rollout status deploy/envoy-gateway --timeout=180s
  k apply -f "$AQUI/envoy.yaml" >/dev/null
}

# Pod de herramientas para medir desde dentro del clúster.
herramientas() {
  local ns="$1"
  k -n "$ns" get pod herramientas >/dev/null 2>&1 && return 0
  k -n "$ns" run herramientas --image=curlimages/curl:8.17.0 --restart=Never \
    --overrides='{"spec":{"terminationGracePeriodSeconds":0}}' -- sleep 100000 >/dev/null
  k -n "$ns" wait --for=condition=Ready pod/herramientas --timeout=120s >/dev/null
}
dentro() { local ns="$1"; shift; k -n "$ns" exec herramientas -- "$@"; }

# Bucle de medición: 5 peticiones por segundo; escribe "epoch código código-de-salida-de-curl" por línea en el registro del pod.
iniciar_medidor() {
  local ns="$1" url="$2" nombre="${3:-medidor}"
  k -n "$ns" delete pod "$nombre" --now --ignore-not-found >/dev/null 2>&1
  k -n "$ns" run "$nombre" --image=curlimages/curl:8.17.0 --restart=Never \
    --overrides='{"spec":{"terminationGracePeriodSeconds":0}}' -- \
    sh -c "while true; do c=\$(curl -s -o /dev/null -m 5 -w '%{http_code} %{exitcode}' $url); echo \"\$(date +%s) \$c\"; sleep 0.2; done" >/dev/null
  k -n "$ns" wait --for=condition=Ready "pod/$nombre" --timeout=120s >/dev/null
}
# resultados_medidor ns [nombre] -> "total errores"
resultados_medidor() {
  local ns="$1" nombre="${2:-medidor}"
  k -n "$ns" logs "$nombre" | awk '{t++; if ($2 != "200") e++} END {printf "%d %d\n", t, e+0}'
}
detener_medidor() { k -n "$1" delete pod "${2:-medidor}" --now --ignore-not-found >/dev/null 2>&1; }

# Baja todo lo que creó la prueba: túneles, clúster, configuración propia e imágenes.
bajar_todo() {
  pkill -f "kubectl --context kind-$CLUSTER .*port-forward" >/dev/null 2>&1 || true
  kind delete cluster --name "$CLUSTER" >/dev/null 2>&1 || true
  rm -f "$KUBECONFIG"
  MANTENER=0 limpiar_imagenes
}

terminar() {
  pkill -f "kubectl --context kind-$CLUSTER .*port-forward" >/dev/null 2>&1 || true
  if [ "${MANTENER:-0}" != 1 ]; then bajar_todo; else limpiar_imagenes; fi
  if [ "$FALLOS" -eq 0 ]; then printf '\n\033[32m%s: todo correcto\033[0m\n' "${1:-prueba}"; exit 0; fi
  printf '\n\033[31m%s: %d fallos\033[0m\n' "${1:-prueba}" "$FALLOS"; exit 1
}
