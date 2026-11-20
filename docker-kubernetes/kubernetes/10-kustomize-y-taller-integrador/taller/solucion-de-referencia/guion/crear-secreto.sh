#!/usr/bin/env bash
# Crea el Secret de un ambiente. El valor se genera aquí y no se escribe en ningún archivo.
#   desarrollo:  ./crear-secreto.sh bitacora-dev
#   producción:  ./crear-secreto.sh bitacora-prod     (en un equipo real: lo trae un gestor externo, no se genera a mano)
set -euo pipefail
# CTX: contexto de kubectl (opcional; por defecto el actual). Así el guion no toca otro clúster por error.
kubectl() { command kubectl ${CTX:+--context "$CTX"} "$@"; }
ns="${1:?espacio de nombres}"
kubectl create namespace "$ns" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl -n "$ns" create secret generic bitacora-api-clave --from-literal=CLAVE_API="$(openssl rand -hex 24)" --dry-run=client -o yaml | kubectl apply -f -
