#!/usr/bin/env bash
# Construye la imagen con salida independiente (output: 'standalone') sin tocar la construcción normal (.next) que miden las pruebas con `next start`.
# Uso: construir-imagen.sh <etiqueta>
set -euo pipefail
cd "$(dirname "$0")/.."
DIST=.next-imagen
rm -rf "$DIST"
SALIDA_INDEPENDIENTE=1 DIST_DIR=$DIST NEXT_TELEMETRY_DISABLED=1 npx next build >"$DIST.log" 2>&1 || { tail -30 "$DIST.log"; exit 1; }
docker build -q --build-arg DIST=$DIST -t "${1:?etiqueta}" . >/dev/null
rm -rf "$DIST" "$DIST.log"
