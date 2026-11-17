#!/usr/bin/env bash
# Dos demostraciones que medir.sh no cubre. Cada una trabaja sobre una copia y no toca el proyecto.
#  1. Paso 8: un error introducido a propósito, la prueba que lo reproduce en rojo, y en verde sin el error.
#  2. Paso 3: un mutante equivalente, escrito a mano: cambia el código y ninguna prueba del dominio puede notarlo.
set -euo pipefail
aqui=$(cd "$(dirname "$0")" && pwd)
trabajo=$(mktemp -d)
trap 'rm -rf "$trabajo"' EXIT

rojas() { # cuenta las pruebas que fallan en el proyecto dado
  (cd "$1" && mvn -q clean test -Dtest="$2" -Dsurefire.failIfNoSpecifiedTests=false >/dev/null 2>&1) || true
  cat "$1"/target/surefire-reports/TEST-*.xml 2>/dev/null | grep -o '<testsuite [^>]*>' | grep -oE '(failures|errors)="[0-9]+"' | grep -oE '[0-9]+' | awk '{s += $1} END {print s + 0}'
}

# --- 1. Paso 8 ---
cp -r "$aqui/kata" "$trabajo/kata"
rm -rf "$trabajo/kata/target"
sed -i.orig 's/filter(numero -> numero < 0)/filter(numero -> numero <= 0)/' "$trabajo/kata/src/main/java/kata/Calculadora.java"
grep -q 'numero <= 0' "$trabajo/kata/src/main/java/kata/Calculadora.java"
con_error=$(rojas "$trabajo/kata" 'CalculadoraTest')
reproduce=$(rojas "$trabajo/kata" 'MutantesTest')
mv "$trabajo/kata/src/main/java/kata/Calculadora.java.orig" "$trabajo/kata/src/main/java/kata/Calculadora.java"
arreglado=$(rojas "$trabajo/kata" 'MutantesTest')
echo "Paso 8: con el error '< 0' -> '<= 0', las pruebas de la kata fallan $con_error; la prueba nueva falla $reproduce; sin el error falla $arreglado"
[ "$con_error" = 0 ] && [ "$reproduce" = 1 ] && [ "$arreglado" = 0 ]

# --- 2. Un mutante equivalente ---
cp -r "$aqui/bicis" "$trabajo/bicis"
rm -rf "$trabajo/bicis/target"
sed -i '' 's/return (int) Math.max(1, (duracion.toSeconds() + dia - 1) \/ dia);/return (int) ((duracion.toSeconds() + dia - 1) \/ dia);/' "$trabajo/bicis/src/main/java/bicis/Tarifa.java"
grep -q 'return (int) ((duracion' "$trabajo/bicis/src/main/java/bicis/Tarifa.java"
equivalente=$(rojas "$trabajo/bicis" '*Test')
echo "Paso 3: sin el Math.max(1, ...) de diasIniciados fallan $equivalente pruebas: equivalente para toda duración no negativa"
[ "$equivalente" = 0 ]
