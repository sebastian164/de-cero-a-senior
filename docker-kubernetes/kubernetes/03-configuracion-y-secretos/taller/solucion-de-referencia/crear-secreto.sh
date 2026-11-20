#!/usr/bin/env bash
# Crea (o reemplaza) el Secret con CLAVE_API en un ambiente. El valor se genera aquí y no se escribe en ningún archivo.
# Uso: ./crear-secreto.sh <espacio-de-nombres>
set -euo pipefail
ns="${1:?espacio de nombres}"
kubectl -n "$ns" create secret generic bitacora-api-clave \
  --from-literal=CLAVE_API="$(openssl rand -hex 24)" \
  --dry-run=client -o yaml | kubectl apply -f -
