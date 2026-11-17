#!/usr/bin/env bash
# Detección automática de deriva entre la especificación y el código. Uso: deriva.sh <carpeta con especificacion.md y Servidor.java>
# Compara los números que la especificación declara con las constantes del código, y que cada criterio tenga una prueba etiquetada.
set -uo pipefail
d=${1:-.}
spec="$d/especificacion.md"; cod="$d/Servidor.java"; feat="$d/pruebas/src/test/java/etiquetas.feature"
dif=0
dato() { echo "  $1"; dif=$((dif + 1)); }

lim_marca=$(grep -oE 'limite-etiquetas: [0-9]+' "$spec" | grep -oE '[0-9]+')
lim_texto=$(grep -E '^6\. ' "$spec" | grep -oE 'más de [0-9]+' | grep -oE '[0-9]+')
lim_cod=$(grep -oE 'MAXIMO_ETIQUETAS = [0-9]+' "$cod" | grep -oE '[0-9]+')
[ "$lim_marca" = "$lim_texto" ] || dato "la especificación se contradice: marca limite-etiquetas=$lim_marca, criterio 6 dice $lim_texto"
[ "$lim_marca" = "$lim_cod" ] || dato "límite de etiquetas: la especificación dice $lim_marca y el código tiene MAXIMO_ETIQUETAS=$lim_cod"

largo_spec=$(grep -oE 'serie de 1 a [0-9]+' "$spec" | grep -oE '[0-9]+$' | head -1)
largo_cod=$(grep -oE 'LARGO_MAXIMO_ETIQUETA = [0-9]+' "$cod" | grep -oE '[0-9]+')
[ "$largo_spec" = "$largo_cod" ] || dato "largo de una etiqueta: la especificación dice $largo_spec y el código $largo_cod"

if grep -q 'sin distinguir mayúsculas' "$spec" && ! grep -q 'toLowerCase(Locale.ROOT)' "$cod"; then dato "la especificación dice que las mayúsculas no distinguen y el código no normaliza"; fi
n=$(sed -n '/^## Criterios de aceptación/,/^## Casos límite/p' "$spec" | grep -cE '^[0-9]+\. ')
for i in $(seq 1 "$n"); do
  grep -q "@criterio-$i\b" "$feat" || grep -qE "^   _Sin prueba automática \(criterio $i\)" "$spec" || dato "el criterio $i no tiene prueba ni justificación"
done
[ "$dif" -eq 0 ] && echo "  sin deriva: límite=$lim_marca, largo=$largo_spec, $n criterios con prueba" && exit 0
exit 1
