#!/usr/bin/env bash
# Solución de referencia del taller SDD 02: reconstruye el historial git (puertas, pruebas en rojo, una tarea por commit)
# en una carpeta temporal y comprueba los criterios. Usa el puerto 48502. No modifica esta carpeta.
set -euo pipefail
aqui=$(cd "$(dirname "$0")" && pwd)
S="$aqui"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}
export PATH="$JAVA_HOME/bin:$PATH"
# Bitácora tal como está en el curso: la línea base sobre la que se construyen las etiquetas.
ORIGINAL="$(cd "$aqui/../../../../.." && pwd)/docker-kubernetes/app/api/Servidor.java"
[ -f "$ORIGINAL" ] || { echo "No encuentro $ORIGINAL" >&2; exit 2; }
trabajo=$(mktemp -d)
pid=""
parar() { [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null || true; pid=""; }
trap 'parar; rm -rf "$trabajo" "$S/pruebas/target"' EXIT
fallos=0
ok() { echo "  ok     $*"; }
falla() { echo "  FALLA  $*"; fallos=$((fallos + 1)); }
CLAVE="sdd2-$RANDOM-$RANDOM"
PUERTO=48502

# Arranca una versión de Servidor.java y devuelve cuando está lista.
arrancar() {  # arrancar <Servidor.java> [carpeta de datos]
  parar
  local d="$trabajo/cls-$RANDOM"; mkdir -p "$d"
  javac -d "$d" "$1" ${EXTRA_JAVA:-}
  (cd "$trabajo"; PUERTO=$PUERTO CLAVE_API="$CLAVE" DIRECTORIO_DATOS="${2:-$trabajo/datos-$RANDOM}" java -cp "$d" Servidor >"$trabajo/servidor.log" 2>&1 &
   echo $! >"$trabajo/pid")
  pid=$(cat "$trabajo/pid")
  for _ in $(seq 1 100); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:$PUERTO/salud/listo)" = 200 ] && return 0; sleep 0.2; done
  return 1
}
# Ejecuta la suite de aceptación contra el servidor en marcha y devuelve "pasan/total".
aceptacion() {  # aceptacion [etiquetas de Karate]
  local salida
  salida=$(cd "$S/pruebas" && mvn -q test -Dbitacora.url=http://localhost:$PUERTO -Dbitacora.clave="$CLAVE" ${1:+-Detiquetas=$1} 2>&1 || true)
  local p t
  p=$(grep -oE 'ESCENARIOS [0-9]+ PASAN [0-9]+' <<<"$salida" | awk '{print $4}'); t=$(grep -oE 'ESCENARIOS [0-9]+' <<<"$salida" | awk '{print $2}')
  echo "${p:-0}/${t:-0}"
}

echo "== El historial (paso 6: una tarea por commit, con su criterio)"
R="$trabajo/repo"
mkdir -p "$R" && cd "$R"
git init -q . && git config user.email "taller@example.com" && git config user.name "Taller SDD"
git config commit.gpgsign false
cp "$ORIGINAL" Servidor.java
git add -A && git commit -q -m "Línea base: Bitácora tal como está"
mkdir -p especificacion && cp "$S/principios.md" "$S/aclaraciones.md" "$S/plan.md" "$S/tareas.md" "$S/especificacion.md" especificacion/
git add -A && git commit -q -m "Puertas 1 a 3: principios, aclaraciones, especificación, plan y tareas revisados"
cp -r "$S/pruebas" pruebas && rm -rf pruebas/target && cp "$S/codigo/PruebaEtiquetas.java" .
git add -A && git commit -q -m "Pruebas de aceptación de los criterios 1 a 15 y PruebaEtiquetas (todas en rojo)"
rojo_commit=$(git rev-parse HEAD)

# Rojo: contra la línea base, las pruebas fallan.
arrancar "$R/Servidor.java"
rojo=$(aceptacion)
EXTRA_JAVA="" ; if javac -d "$trabajo/pe" Servidor.java PruebaEtiquetas.java 2>/dev/null; then rojo_unit="compila"; else rojo_unit="no compila (falta etiquetas())"; fi
echo "  rojo: aceptación $rojo; PruebaEtiquetas $rojo_unit"
[ "${rojo%/*}" -lt "${rojo#*/}" ] && ok "las pruebas existían antes que el código y fallan sobre la línea base ($rojo pasan: solo las que ya se cumplían hoy, por ejemplo 'sin parámetro responde como siempre')" || falla "la suite no falla antes de implementar"

hacer() {  # hacer <etapa> <mensaje>
  cp "$S/codigo/etapas/etapa$1.java" Servidor.java
  git add -A && git commit -q -m "$2"
  arrancar "$R/Servidor.java"
  echo "  $(git log -1 --format=%s | cut -c1-70) -> aceptación $(aceptacion)"
}
hacer 1 "T1: función pura etiquetas() y constantes (criterios 1, 2, 3)"
d="$trabajo/pe"; rm -rf "$d"; mkdir -p "$d"; javac -d "$d" Servidor.java PruebaEtiquetas.java && java -cp "$d" PruebaEtiquetas | sed 's/^/        /'
hacer 2 "T2: POST responde etiquetas (criterios 4 y 5)"
hacer 3 "T3: rechazar más de 10 etiquetas distintas (criterio 6)"
hacer 4 "T4: GET filtra por etiqueta (criterios 7 a 10, 12 a 14)"
hacer 5 "T5: validar la etiqueta de la consulta (criterio 11)"
final=$(aceptacion)
[ "${final%/*}" = "${final#*/}" ] && ok "al final pasan todas las pruebas de aceptación: $final" || falla "al final pasan $final"
git log --format='%h %s' | sed 's/^/        /'
orden_ok=$(git log --reverse --format=%s | awk '/^Pruebas de aceptación/{p=NR} /^T1:/{t=NR} END{print (p>0 && t>0 && p<t) ? "si" : "no"}')
[ "$orden_ok" = si ] && ok "el commit de las pruebas precede al de la primera tarea (historial)" || falla "las pruebas no preceden al código"
# Cada commit de tarea nombra su criterio.
sin_criterio=$(git log --format=%s | grep -E '^T[0-9]:' | grep -vc 'criterio' || true)
[ "$sin_criterio" -eq 0 ] && ok "cada commit de tarea nombra el criterio que cumple" || falla "hay commits de tarea sin criterio"
parar

echo "== Paso 4: la trazabilidad"
n=15; sin=0
for i in $(seq 1 $n); do grep -q "@criterio-$i\b" "$S/pruebas/src/test/java/etiquetas.feature" || { falla "el criterio $i no tiene prueba etiquetada"; sin=$((sin + 1)); }; grep -qE "\| (T[0-9], )*T[0-9]" "$S/tareas.md" || true; done
[ "$sin" -eq 0 ] && ok "los 15 criterios tienen al menos un escenario con su etiqueta (@criterio-N)" || true
huerfanas=$(awk -F'|' '/^\| T[0-9]/{print $2, $4}' "$S/tareas.md" | awk '$2=="" {print $1}' | wc -l | tr -d ' ')
[ "$huerfanas" -eq 0 ] && ok "ninguna tarea queda sin criterio" || falla "$huerfanas tareas sin criterio"

echo "== Paso 8: tabla final, criterio por criterio, ejecutando cada prueba"
arrancar "$S/codigo/Servidor.java"
printf '| Criterio | Escenarios | Resultado |\n|---|---|---|\n' >"$trabajo/tabla.md"
todo=0
for i in $(seq 1 15); do
  r=$(aceptacion "@criterio-$i")
  printf '| %s | %s | %s |\n' "$i" "$r" "$([ "${r%/*}" = "${r#*/}" ] && echo pasa || { echo FALLA; todo=1; })" >>"$trabajo/tabla.md"
done
cat "$trabajo/tabla.md" | sed 's/^/        /'
[ "$todo" -eq 0 ] && ok "los 15 criterios pasan, cada uno por su etiqueta" || falla "algún criterio falla"
diff -q "$trabajo/tabla.md" "$aqui/tabla-verificacion.md" >/dev/null && ok "la tabla coincide con tabla-verificacion.md" || falla "la tabla difiere de tabla-verificacion.md"
# Casos límite manuales (paso 8): lo que Karate no recorre.
U=localhost:$PUERTO
c1=$(curl -s -o /dev/null -w '%{http_code}' "$U/api/notas?etiqueta=%23%23a")
c2=$(curl -s -o /dev/null -w '%{http_code}' "$U/api/notas?etiqueta=")
c3=$(curl -s -o /dev/null -w '%{http_code}' "$U/api/notas?etiqueta=#a")
c4=$(curl -s "$U/api/notas?etiqueta=a&etiqueta=zzz" | head -c 40)
echo "        revisión manual: ?etiqueta=%23%23a -> $c1; ?etiqueta= -> $c2; ?etiqueta=#a sin codificar (curl corta el fragmento) -> $c3; etiqueta repetida -> $c4"
[ "$c1" = 400 ] && [ "$c2" = 400 ] && [ "$c3" = 400 ] && ok "casos límite manuales: dos # y vacío son 400; un # sin codificar llega vacío y es 400 (aclaración 2)" || falla "casos límite"
# Todos los de "fuera de alcance" ausentes del código.
r=$(curl -s -o /dev/null -w '%{http_code}' "$U/api/etiquetas")
[ "$r" = 404 ] && ok "fuera de alcance: no existe /api/etiquetas (404)" || falla "/api/etiquetas responde $r"
grep -qE '"/api/etiquetas"|etiquetas=|List<String> etiquetasConsulta' "$S/codigo/Servidor.java" && falla "el código contiene algo de fuera de alcance" || ok "fuera de alcance: el código no tiene listado de etiquetas ni filtro por varias"
parar

echo "== Paso 9: el experimento de control (una sola frase, sin especificación)"
echo "        (la versión de control es un atajo típico escrito a mano; ver ANALISIS.md)"
arrancar "$S/control/Servidor.java"
control=$(aceptacion)
echo "        suite de aceptación contra la versión de control: $control"
[ "${control%/*}" -lt "${control#*/}" ] && ok "la versión sin especificación no cumple las pruebas de aceptación ($control)" || falla "la versión de control pasa todo"
f=$(curl -s -o /dev/null -w '%{http_code}' "$U/api/etiquetas")
[ "$f" = 200 ] && ok "y añadió algo de fuera de alcance: GET /api/etiquetas responde 200" || falla "no añadió /api/etiquetas"
forma=$(curl -s -X POST -H "X-Clave: $CLAVE" -d "x #a" "$U/api/notas" >/dev/null; curl -s "$U/api/notas" | head -c 120)
echo "        la forma de GET /api/notas cambió: $forma"
echo "$forma" | grep -q '"texto"' && ok "rompió la forma de la respuesta de siempre (notas pasó de lista de textos a lista de objetos)" || falla "no cambió la forma"
parar
[ "$fallos" -eq 0 ]
