#!/usr/bin/env bash
# De nada a Bitácora funcionando con una orden:  ./construir.sh <desarrollo|produccion>
# Variables: CLUSTER (nombre del clúster), PUERTO_ENTRADA (puerto del equipo para la entrada, 50080 por defecto), IMAGEN_API_1_0_0, IMAGEN_API_1_0_1, IMAGEN_WEB (imágenes locales ya construidas).
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
CLUSTER="${CLUSTER:-bitacora}"
AMBIENTE="${1:-produccion}"
KUBECONFIG="${KUBECONFIG:?define KUBECONFIG para no tocar el del equipo}"; export KUBECONFIG
CTX="kind-$CLUSTER"
k() { kubectl --context "$CTX" "$@"; }
cargar() { # imagen_local nombre_en_el_clúster
  local tar; tar="$(mktemp "${TMPDIR:-/tmp}/imagen.XXXXXX")"
  docker save --platform "linux/$(docker version --format '{{.Server.Arch}}')" -o "$tar" "$1"
  kind load image-archive --name "$CLUSTER" "$tar" >/dev/null; rm -f "$tar"
  for n in $(kind get nodes --name "$CLUSTER"); do docker exec "$n" ctr -n k8s.io images tag --force "docker.io/library/$1" "docker.io/library/$2" >/dev/null; done
}
kind get clusters | grep -qx "$CLUSTER" || awk -v n="$CLUSTER" 'NR==1{print; print "name: " n; next} 1' "$AQUI/kind.yaml" | sed "s/hostPort: 50080/hostPort: ${PUERTO_ENTRADA:-50080}/" | kind create cluster --name "$CLUSTER" --config - --wait 180s
cargar "${IMAGEN_API_1_0_0:?}" bitacora-api:1.0.0
cargar "${IMAGEN_API_1_0_1:?}" bitacora-api:1.0.1
cargar "${IMAGEN_WEB:?}" bitacora-web:0.1.0
k apply -f "https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.9.1/components.yaml" >/dev/null
# El parche solo se aplica si falta el argumento: el guion se puede repetir sobre un clúster que ya existe.
k -n kube-system get deployment metrics-server -o jsonpath='{.spec.template.spec.containers[0].args}' | grep -q kubelet-insecure-tls ||
  k -n kube-system patch deployment metrics-server --type=json -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' >/dev/null
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm --version v1.9.3 -f "$AQUI/valores-envoy-gateway.yaml" -n envoy-gateway-system --create-namespace --kube-context "$CTX" --wait --timeout 5m >/dev/null
k apply -f "$AQUI/entrada.yaml" >/dev/null
case "$AMBIENTE" in desarrollo) NS=bitacora-dev;; produccion) NS=bitacora-prod;; *) echo "ambiente desconocido"; exit 1;; esac
CTX="$CTX" "$AQUI/crear-secreto.sh" "$NS"
k apply -k "$AQUI/../superposiciones/$AMBIENTE"
k -n "$NS" rollout status deploy/bitacora-api --timeout=300s
k -n "$NS" rollout status deploy/bitacora-web --timeout=300s
