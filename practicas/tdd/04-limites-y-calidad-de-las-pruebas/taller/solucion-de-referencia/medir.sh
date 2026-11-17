#!/usr/bin/env bash
# Mide cobertura (JaCoCo) y mutación (PIT) y guarda los informes en informes/<proyecto>-<juego>/.
#   kata-antes:        la kata tal como quedó en la sesión 01, con sus pruebas
#   kata-experimento:  pruebas sin una sola comprobación (paso 5), sobre la kata de esta sesión
#   kata-despues:      la kata de esta sesión con todas sus pruebas, salvo el experimento
#   bicis-antes:       el caso de uso tal como quedó en la sesión 02, con sus pruebas
#   bicis-despues:     el caso de uso de esta sesión con las pruebas nuevas
# Imprime una línea por juego y deja resumen.txt; resumen.esperado trae los números que debe dar.
# Los informes van a la carpeta INFORMES (por defecto, informes/ junto a este guion).
set -euo pipefail
aqui=$(cd "$(dirname "$0")" && pwd)
informes=${INFORMES:-$aqui/informes}
rm -rf "$informes"
mkdir -p "$informes"
: >"$informes/resumen.txt"

medir() {
  local proyecto=$1 juego=$2 origen=$3 surefire=$4 pit=$5
  local dir="$aqui/$proyecto"
  if [ "$origen" != "-" ]; then
    dir="$informes/trabajo/$proyecto-$juego"
    mkdir -p "$dir"
    cp "$aqui/$proyecto/pom.xml" "$dir/"
    cp -r "$origen/src" "$dir/"
  fi
  (
    cd "$dir"
    mvn -q clean test -Dtest="$surefire" -Dsurefire.failIfNoSpecifiedTests=false >"$informes/$proyecto-$juego.log" 2>&1
    mvn -q org.pitest:pitest-maven:mutationCoverage -DtargetTests="$pit" >>"$informes/$proyecto-$juego.log" 2>&1
  ) || { echo "Falló la medición $proyecto-$juego:"; tail -40 "$informes/$proyecto-$juego.log"; exit 1; }
  local salida="$informes/$proyecto-$juego"
  mkdir -p "$salida"
  cp -r "$dir/target/site/jacoco" "$salida/cobertura"
  cp -r "$dir/target/pit-reports" "$salida/mutacion"
  {
    awk -F, -v juego="$proyecto-$juego" 'NR > 1 {lm += $8; lc += $9; bm += $6; bc += $7}
      END {printf "%-17s lineas %d/%d (%.0f %%)  ramas %d/%d (%.0f %%)", juego, lc, lm + lc, 100 * lc / (lm + lc), bc, bm + bc, (bm + bc) ? 100 * bc / (bm + bc) : 100}' \
      "$salida/cobertura/jacoco.csv"
    awk -F, '{n++; s[$6]++} END {printf "  mutantes %d  muertos %d  sobreviven %d  sin cobertura %d\n", n, s["KILLED"] + s["TIMED_OUT"], s["SURVIVED"], s["NO_COVERAGE"]}' \
      "$salida/mutacion/mutations.csv"
  } | tee -a "$informes/resumen.txt" || true
  grep -v ',KILLED,' "$salida/mutacion/mutations.csv" | awk -F, '{printf "    %s %s.%s línea %s: %s\n", $6, $2, $4, $5, $3}' | sed 's/org.pitest.mutationtest.engine.gregor.mutators.//' | tee -a "$informes/resumen.txt" || true
}

sesion01="$aqui/../../../01-rojo-verde-refactor/taller/solucion-de-referencia"
sesion02="$aqui/../../../02-de-afuera-hacia-adentro/taller/solucion-de-referencia"
medir kata antes "$sesion01" 'CalculadoraTest' 'kata.*'
medir kata experimento - 'SinComprobacionesTest' 'kata.SinComprobacionesTest'
medir kata despues - '!SinComprobacionesTest' 'kata.CalculadoraTest,kata.MutantesTest,kata.PropiedadesTest,kata.RegresionTest'
medir bicis antes "$sesion02" '*Test' 'bicis.*'
medir bicis despues - '*Test' 'bicis.*'
rm -rf "$informes/trabajo"
