#!/usr/bin/env bash
# Comprueba lo comprobable del taller SDD 01: cada criterio tiene casos, no hay decisiones de implementación,
# hay al menos 15 preguntas y las abiertas traen respuesta por defecto, y los tres escenarios de Karate están bien formados.
set -euo pipefail
aqui=$(cd "$(dirname "$0")" && pwd)
spec="$aqui/especificacion.md"
fallos=0
ok() { echo "  ok     $*"; }
falla() { echo "  FALLA  $*"; fallos=$((fallos + 1)); }

n=$(sed -n '/^## Criterios de aceptación/,/^## Casos límite/p' "$spec" | grep -cE '^[0-9]+\. ')
echo "== Criterios: $n"
sin=0
for i in $(seq 1 "$n"); do
  c=$(awk -F'|' -v i="$i" 'NR>6 && $3+0==i {c++} END{print c+0}' "$aqui/casos.md")
  [ "$c" -ge 1 ] || { falla "el criterio $i no tiene ningún caso de prueba"; sin=$((sin + 1)); }
done
[ "$sin" -eq 0 ] && ok "los $n criterios tienen al menos un caso de prueba derivado en casos.md ($(grep -c '^| C' "$aqui/casos.md") casos)"

echo "== Cada criterio usa notación EARS"
ears=$(sed -n '/^## Criterios de aceptación/,/^## Casos límite/p' "$spec" | grep -E '^[0-9]+\. ' | grep -cE '^[0-9]+\. (Cuando|Si|El sistema deberá)')
[ "$ears" -eq "$n" ] && ok "los $n criterios empiezan con Cuando / Si / El sistema deberá" || falla "solo $ears de $n criterios siguen EARS"

echo "== Sin decisiones de implementación"
# Palabras que delatan un cómo: clases, funciones, bibliotecas, expresiones regulares, estructuras de datos.
if sed -n '1,/^## Preguntas abiertas/p' "$spec" | grep -inE 'regex|expresi[oó]n regular|funci[oó]n|clase |HashMap|lista enlazada|base de datos|índice|Locale|\.java|bucle|algoritmo|método privado'; then falla "hay palabras de implementación en la especificación"; else ok "ninguna palabra de implementación (regex, función, clase, índice, Locale, base de datos…)"; fi
grep -q 'Locale' "$aqui/notas-para-el-plan.md" && ok "las decisiones de implementación están en notas-para-el-plan.md"

echo "== Secciones"
for s in "Propósito" "Historias de usuario" "Definiciones" "Criterios de aceptación" "Casos límite" "Fuera de alcance" "Restricciones" "Preguntas abiertas"; do
  grep -q "^## $s" "$spec" && ok "sección: $s" || falla "falta la sección $s"
done
fa=$(sed -n '/^## Fuera de alcance/,/^## Restricciones/p' "$spec" | grep -c '^- ')
[ "$fa" -ge 5 ] && ok "fuera de alcance: $fa elementos (mínimo 5)" || falla "fuera de alcance tiene solo $fa"

echo "== Preguntas"
q=$(grep -cE '^\| [0-9]+ \|' "$aqui/preguntas.md")
ab=$(grep -c '\*\*abierta\*\*' "$aqui/preguntas.md")
[ "$q" -ge 15 ] && ok "$q preguntas (mínimo 15), $ab abiertas" || falla "solo $q preguntas"
sin_def=$(grep '\*\*abierta\*\*' "$aqui/preguntas.md" | grep -vc 'por defecto' || true)
[ "$sin_def" -eq 0 ] && ok "las $ab preguntas abiertas traen respuesta por defecto" || falla "$sin_def abiertas sin respuesta por defecto"
# Las preguntas abiertas de la especificación coinciden con las del paso 2.
aq=$(sed -n '/^## Preguntas abiertas/,$p' "$spec" | grep -c '^- ')
[ "$aq" -eq "$ab" ] && ok "la especificación lista las mismas $aq preguntas abiertas, cada una con su valor por defecto" || falla "preguntas abiertas: $aq en la especificación, $ab en preguntas.md"
sed -n '/^## Preguntas abiertas/,$p' "$spec" | grep '^- ' | grep -vq 'Por defecto' && falla "una pregunta abierta de la especificación no dice su valor por defecto" || ok "todas las preguntas de la especificación dicen su valor por defecto"

echo "== Escenarios de Karate (paso 9): bien formados"
f="$aqui/karate/etiquetas-escenarios.feature"
[ "$(grep -cE '^\s+Scenario' "$f")" -eq 3 ] && ok "tres escenarios (guardar, filtrar, etiqueta inválida)" || falla "no son tres escenarios"
[ "$fallos" -eq 0 ]
