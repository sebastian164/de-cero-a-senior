#!/usr/bin/env bash
# Taller nivel-06/01, punto 6: construcción con el compilador de React activado y la misma medición sin memorización manual.
set -euo pipefail
cd "$(dirname "$0")/.."
export NEXT_TELEMETRY_DISABLED=1
TMP=$(mktemp -d "$PWD/.compilador-XXXXXX")   # registros dentro del proyecto, no en /tmp fijo
rm -rf .next-compilador
COMPILADOR_REACT=1 DIST_DIR=.next-compilador npx next build >"$TMP/build.log" 2>&1 || { tail -30 "$TMP/build.log"; rm -rf "$TMP"; exit 1; }
DIST_DIR=.next-compilador npx next start -p 49512 -H 127.0.0.1 >"$TMP/start.log" 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null || true; rm -rf .next-compilador "$TMP"' EXIT
for i in $(seq 1 40); do curl -sf -o /dev/null http://127.0.0.1:49512/n6/01-rendimiento/compilador && break; sleep 1; done
PUERTO_APP=49512 SIN_SERVIDOR=1 COMPILADOR=1 npx playwright test e2e/n6-01.spec.ts -g "punto 6"
