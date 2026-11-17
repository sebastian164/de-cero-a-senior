#!/usr/bin/env bash
# Los casos "fallo-*" y js-original DEBEN fallar al compilar, y por el motivo que el README anuncia. Cada caso exige código de salida
# distinto de 0 y los errores esperados en la salida de `tsc -p`. La salida real queda en mediciones/errores-del-compilador/<caso>.txt
# (de ahí sale la lista de errores del ANALISIS.md del taller de TypeScript 01).
set -uo pipefail
cd "$(dirname "$0")/.."
mkdir -p mediciones/errores-del-compilador
fallos=0
caso() { # caso <carpeta> <nombre> <patrón esperado>...
  local dir=$1 nombre=$2; shift 2
  local salida; salida=$(npx tsc -p "$dir" 2>&1); local c=$?
  printf '%s\n' "$salida" > "mediciones/errores-del-compilador/$nombre.txt"
  if [ $c -eq 0 ]; then echo "FALLA  $nombre: compiló, y no debía"; fallos=$((fallos+1)); return; fi
  for patron in "$@"; do
    if ! grep -q -- "$patron" <<<"$salida"; then echo "FALLA  $nombre: falta el error esperado «$patron»"; fallos=$((fallos+1)); return; fi
  done
  echo "ok     $nombre: no compila ($(grep -c 'error TS' <<<"$salida") errores; esperados: $*)"
}
T=talleres
ANALISIS_TS01=../../typescript/01-typescript-esencial/taller/solucion-de-referencia/ANALISIS.md
caso $T/typescript/01-typescript-esencial/fallo-medio-nuevo ts01-medio-nuevo "TS2322" "'never'"
caso $T/typescript/01-typescript-esencial/fallo-conversion-directa ts01-conversion-directa TS18048 TS2551 TS2540 TS7053 TS2554
caso $T/typescript/01-typescript-esencial/js-original ts01-js-original TS7006 TS18048 TS2551 TS7053
caso $T/react-next/nivel-02-solid/02-ocp/fallo-tipo-sin-vista n2-02-tipo-sin-vista TS2741 "'envio' is missing"
caso $T/react-next/nivel-05-arquitectura/02-datos-cache-y-mutaciones/fallo-revalidate-un-argumento n5-02-revalidate-un-argumento TS2554 "Expected 2 arguments, but got 1"
# El ANALISIS.md de TypeScript 01 debe nombrar cada código de error real que dio la conversión directa y el JavaScript original.
for codigo in $(grep -ho 'TS[0-9]\{4,5\}' mediciones/errores-del-compilador/ts01-conversion-directa.txt mediciones/errores-del-compilador/ts01-js-original.txt | sort -u); do
  grep -q "$codigo" $ANALISIS_TS01 || { echo "FALLA  ANALISIS.md de TS 01 no menciona $codigo"; fallos=$((fallos+1)); }
done
exit $((fallos>0))
