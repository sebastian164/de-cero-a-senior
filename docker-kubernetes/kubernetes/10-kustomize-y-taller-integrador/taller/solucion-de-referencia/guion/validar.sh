#!/usr/bin/env bash
# Validación previa sin clúster (apta para integración continua): esquemas de Kubernetes y buenas prácticas.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
D="$(mktemp -d "${TMPDIR:-/tmp}/bitacora-validar.XXXXXX")"; trap 'rm -rf "$D"' EXIT
for amb in desarrollo produccion; do
  kubectl kustomize "$AQUI/../superposiciones/$amb" > "$D/bitacora-$amb.yaml"
  kubeconform -strict -summary -ignore-missing-schemas \
    -schema-location default \
    -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json' \
    "$D/bitacora-$amb.yaml"
  kube-linter lint "$D/bitacora-$amb.yaml" --config "$AQUI/kube-linter.yaml"
done
