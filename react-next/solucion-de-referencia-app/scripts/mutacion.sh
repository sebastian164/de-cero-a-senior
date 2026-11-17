#!/usr/bin/env bash
# Taller nivel-04/03, punto 6: se introduce un error en el código y se verifica que alguna prueba falla.
set -u
cd "$(dirname "$0")/.."
ARCH=proyectos/react-next/n3-03-carrito/reductor.ts
TMP=$(mktemp -d "$PWD/.mutacion-XXXXXX")   # copia del original dentro del proyecto (no en /tmp fijo): dos ejecuciones no se pisan
cp "$ARCH" "$TMP/reductor.original.ts"
trap 'cp "$TMP/reductor.original.ts" "$ARCH"; rm -rf "$TMP"' EXIT
# Error introducido: al repetir un producto no se suma la cantidad.
sed -i '' 's/cantidad: l.cantidad + 1/cantidad: l.cantidad/' "$ARCH"
if cmp -s "$ARCH" "$TMP/reductor.original.ts"; then echo "FALLO: la mutación no cambió el archivo"; exit 1; fi
SALIDA=$(npx vitest run talleres/react-next/nivel-04-clean-code-y-testing/03-pruebas 2>&1)
echo "$SALIDA" | grep -E "FAIL|Tests " | head -6
if echo "$SALIDA" | grep -q "failed"; then echo "OK: las pruebas detectaron el error introducido"; exit 0; fi
echo "FALLO: las pruebas siguen pasando con el error"; exit 1
