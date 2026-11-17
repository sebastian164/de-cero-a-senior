#!/usr/bin/env bash
# Punto 7: aplica de verdad los esquemas oficiales de migración a una COPIA de contador-antes.component.ts y comprueba que
# el resultado coincide (salvo espacios y los nombres de clase y selector) con resultado/contador-migrado.component.ts.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; RAIZ="$(cd "$AQUI/../../../.." && pwd)"; cd "$RAIZ"
TMP="$AQUI/_ejecucion"; rm -rf "$TMP"; mkdir -p "$TMP"; trap 'rm -rf "$TMP"' EXIT
cp "$AQUI/contador-antes.component.ts" "$TMP/contador-antes.component.ts"
CI=true npx ng g @angular/core:signals --path "${TMP#$RAIZ/}" --migrations inputs --migrations outputs --migrations queries --defaults > "$RAIZ/tmp/migracion-signals.log" 2>&1
norm() { sed -e 's/ContadorAntesComponent/X/g; s/ContadorMigradoComponent/X/g; s/app-contador-antes/S/g; s/app-contador-migrado/S/g' "$1" | tr -d ' \n\t' | sed 's/,)/)/g; s/,}/}/g'; }
grep -q 'input(' "$TMP/contador-antes.component.ts" && grep -q 'output<number>' "$TMP/contador-antes.component.ts" && grep -q 'viewChild<' "$TMP/contador-antes.component.ts" \
  || { echo "FALLO: el esquema no migró entradas, salidas y consultas"; cat "$RAIZ/tmp/migracion-signals.log"; exit 1; }
[ "$(norm "$TMP/contador-antes.component.ts")" = "$(norm "$AQUI/resultado/contador-migrado.component.ts")" ] \
  || { echo "FALLO: la salida real difiere de resultado/contador-migrado.component.ts"; diff "$TMP/contador-antes.component.ts" "$AQUI/resultado/contador-migrado.component.ts"; exit 1; }
echo "MIGRACION OK: el esquema produjo el resultado guardado"
