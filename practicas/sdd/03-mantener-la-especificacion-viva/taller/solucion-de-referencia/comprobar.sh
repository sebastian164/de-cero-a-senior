#!/usr/bin/env bash
# Solución de referencia del taller SDD 03: reconstruye en una carpeta temporal el repositorio de las etiquetas
# y le hace los cambios del taller, en orden. Usa el puerto 48520. No modifica esta carpeta.
set -euo pipefail
aqui=$(cd "$(dirname "$0")" && pwd)
S="$aqui"
# Punto de partida: la solución de referencia de la sesión 02 (ruta relativa dentro del repositorio).
S2="$aqui/../../../02-del-plan-a-las-tareas/taller/solucion-de-referencia"
[ -f "$S2/codigo/Servidor.java" ] || { echo "No encuentro la solución de referencia de la sesión 02 en $S2" >&2; exit 2; }
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}
export PATH="$JAVA_HOME/bin:$PATH"
trabajo=$(mktemp -d)
pid=""
parar() { [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null || true; pid=""; }
trap 'parar; rm -rf "$trabajo"' EXIT
fallos=0
ok() { echo "  ok     $*"; }
falla() { echo "  FALLA  $*"; fallos=$((fallos + 1)); }
CLAVE="sdd3-$RANDOM-$RANDOM"
PUERTO=48520
R="$trabajo/repo"

arrancar() {
  parar
  local d="$trabajo/cls-$RANDOM"; mkdir -p "$d"
  javac -d "$d" "$R/Servidor.java"
  (cd "$trabajo"; PUERTO=$PUERTO CLAVE_API="$CLAVE" DIRECTORIO_DATOS="$trabajo/datos-$RANDOM" java -cp "$d" Servidor >"$trabajo/servidor.log" 2>&1 &
   echo $! >"$trabajo/pid")
  pid=$(cat "$trabajo/pid")
  for _ in $(seq 1 100); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:$PUERTO/salud/listo)" = 200 ] && return 0; sleep 0.2; done
  return 1
}
aceptacion() {  # devuelve "pasan/total" de la suite del repo
  arrancar
  local salida p t
  salida=$(cd "$R/pruebas" && mvn -q test -Dbitacora.url=http://localhost:$PUERTO -Dbitacora.clave="$CLAVE" 2>&1 || true)
  p=$(grep -oE 'ESCENARIOS [0-9]+ PASAN [0-9]+' <<<"$salida" | awk '{print $4}'); t=$(grep -oE 'ESCENARIOS [0-9]+' <<<"$salida" | awk '{print $2}')
  parar
  echo "${p:-0}/${t:-0}"
}
verde() { local r; r=$(aceptacion); [ "${r%/*}" = "${r#*/}" ]; }

echo "== Punto de partida: el estado final de la sesión 02"
mkdir -p "$R" && cd "$R"
git init -q -b main . && git config user.email "taller@example.com" && git config user.name "Taller SDD" && git config commit.gpgsign false
printf "target/\n" >.gitignore
cp "$S2/codigo/Servidor.java" Servidor.java
cp "$S2/especificacion.md" especificacion.md
cp -r "$S2/pruebas" pruebas && rm -rf pruebas/target
mkdir -p herramientas && cp "$S/herramientas/"* herramientas/
git add -A && git commit -q -m "Etiquetas: estado al cerrar la sesión 02"
r=$(aceptacion); echo "  aceptación: $r"
[ "${r%/*}" = "${r#*/}" ] && ok "punto de partida en verde ($r)" || falla "el punto de partida no está en verde"

echo "== Paso 1: enlazar cada criterio con la prueba que lo vigila"
python3 herramientas/anotar.py especificacion.md
if bash herramientas/deriva.sh . >/dev/null; then ok "cada uno de los 15 criterios nombra su prueba y esa prueba existe"; else falla "hay criterios sin prueba"; fi
echo "        criterios anotados: $(grep -c '_Prueba: `@criterio-' especificacion.md); criterios sin prueba ni justificación: 0"
git add -A && git commit -q -m "Enlaza cada criterio con su prueba (@criterio-N) y añade herramientas/deriva.sh"
# La herramienta de verdad detecta un criterio sin prueba: se quita una etiqueta en una copia.
cp -r "$R" "$trabajo/copia-sin-prueba"; sed -i '' 's/@criterio-14//' "$trabajo/copia-sin-prueba/pruebas/src/test/java/etiquetas.feature"
if bash herramientas/deriva.sh "$trabajo/copia-sin-prueba" >"$trabajo/salida.txt"; then falla "deriva.sh no vio la prueba que falta"; else ok "si se quita la etiqueta de un criterio, deriva.sh lo señala: $(grep -m1 criterio "$trabajo/salida.txt" | sed 's/^ *//')"; fi

echo "== Paso 2: un cambio en orden (máximo cinco etiquetas), un solo pull request"
git checkout -q -b cambio-005-maximo-cinco
cat >cambio-005.md <<'MD'
# Cambio 005: como máximo cinco etiquetas por nota

## Motivo

Notas con diez etiquetas se leen como una nube de palabras y no ayudan a agrupar. Se pide un máximo de cinco.

## Cambio en la especificación

- Criterio 6, antes: "Si una nota contiene más de 10 etiquetas distintas, entonces el sistema deberá responder 400…"
- Criterio 6, ahora: "Si una nota contiene más de 5 etiquetas distintas, entonces el sistema deberá responder 400…"
- Marca `limite-etiquetas`: de 10 a 5.

## Lo que no cambia

Todo lo demás. En particular, la forma del error y que las etiquetas repetidas cuentan una sola vez.

## Pruebas afectadas

- `etiquetas.feature`, `@criterio-6`: los límites pasan de 10/11 a 5/6.

## Riesgo

Las notas ya guardadas con entre 6 y 10 etiquetas siguen existiendo y se pueden filtrar: el límite solo aplica al guardar.
MD
git add -A && git commit -q -m "Cambio 005: documento de cambio (máximo cinco etiquetas)"
sed -i '' 's/<!-- limite-etiquetas: 10 -->/<!-- limite-etiquetas: 5 -->/; s/Si una nota contiene más de 10 etiquetas distintas/Si una nota contiene más de 5 etiquetas distintas/; s/- ¿Basta con 10 etiquetas por nota? Por defecto: sí./- ¿Basta con 5 etiquetas por nota? Por defecto: sí./' especificacion.md
git add -A && git commit -q -m "Cambio 005: especificación (criterio 6 y marca de límite)"
python3 - <<'PY'
p='pruebas/src/test/java/etiquetas.feature'; s=open(p).read()
s=s.replace("Scenario: más de 10 etiquetas distintas se rechaza y la nota no se guarda\n    * def once = '#a1 #a2 #a3 #a4 #a5 #a6 #a7 #a8 #a9 #a10 #a11 ' + sufijo","Scenario: más de 5 etiquetas distintas se rechaza y la nota no se guarda\n    * def once = '#a1 #a2 #a3 #a4 #a5 #a6 ' + sufijo")
s=s.replace("Scenario: diez etiquetas distintas se aceptan, aunque una se repita","Scenario: cinco etiquetas distintas se aceptan, aunque una se repita")
s=s.replace("And request '#a1 #a2 #a3 #a4 #a5 #a6 #a7 #a8 #a9 #a10 #A10 ' + sufijo","And request '#a1 #a2 #a3 #a4 #a5 #A5 ' + sufijo")
s=s.replace("And match response.etiquetas == ['a1','a2','a3','a4','a5','a6','a7','a8','a9','a10']","And match response.etiquetas == ['a1','a2','a3','a4','a5']")
open(p,'w').write(s)
PY
git add -A && git commit -q -m "Cambio 005: pruebas (@criterio-6 con límites 5 y 6)"
rojo=$(aceptacion); echo "        con la especificación y las pruebas nuevas y el código viejo: $rojo"
[ "${rojo%/*}" -lt "${rojo#*/}" ] && ok "las pruebas nuevas fallan antes de tocar el código ($rojo): el cambio está en rojo" || falla "las pruebas nuevas no fallan"
if bash herramientas/deriva.sh . >"$trabajo/salida.txt"; then falla "deriva.sh no vio que el código aún dice 10"; else ok "y deriva.sh ya avisa de la deriva: $(grep -m1 'límite' "$trabajo/salida.txt" | sed 's/^ *//')"; fi
sed -i '' 's/MAXIMO_ETIQUETAS = 10;/MAXIMO_ETIQUETAS = 5;/' Servidor.java
git add -A && git commit -q -m "Cambio 005: código (MAXIMO_ETIQUETAS = 5)"
verde && ok "con el código nuevo, todo en verde: $(aceptacion)" || falla "el cambio no queda en verde"
bash herramientas/deriva.sh . >/dev/null && ok "deriva.sh: sin deriva" || falla "deriva.sh sigue avisando"
git checkout -q main && git merge -q --no-ff cambio-005-maximo-cinco -m "Pull request: cambio 005 (máximo cinco etiquetas)"
echo "        orden de los commits del PR: $(git log --reverse --format=%s main | grep '^Cambio 005' | sed 's/Cambio 005: //' | cut -c1-30 | paste -sd'|' -)"
[ "$(git log --reverse --format=%s main | grep '^Cambio 005' | awk -F': ' '{print $2}' | awk '{print $1}' | head -4 | paste -sd, -)" = "documento,especificación,pruebas,código" ] && ok "el historial muestra el orden: documento, especificación, pruebas, código; un solo PR (merge --no-ff)" || falla "el orden del PR no es el esperado"

echo "== Paso 3: un cambio al revés (el comportamiento cambia en el código, sin tocar la especificación)"
git checkout -q -b atajo
sed -i '' 's/                        buscada = buscada.toLowerCase(Locale.ROOT);/                        \/\/ atajo: sin normalizar/' Servidor.java
grep -q 'atajo: sin normalizar' Servidor.java || falla "no se aplicó el atajo"
git add -A && git commit -q -m "Atajo: la consulta por etiqueta distingue mayúsculas"
if bash herramientas/deriva.sh . >/dev/null; then echo "        deriva.sh no ve este atajo (solo compara números y la normalización de guardar): hace falta la prueba"; fi
r=$(aceptacion); echo "        suite de aceptación: $r"
[ "${r%/*}" -lt "${r#*/}" ] && ok "lo que avisó de la deriva fue una prueba que falla (@criterio-8: la consulta con otras mayúsculas), de forma automática" || falla "ninguna prueba avisó del atajo"
fallo=$(cd "$R/pruebas" && { arrancar; mvn -q test -Dbitacora.url=http://localhost:$PUERTO -Dbitacora.clave="$CLAVE" 2>&1 || true; parar; } | grep -E '^    - ' | head -3 | sed 's/^ *- //' | paste -sd';' -)
echo "        escenarios que fallan: $fallo"
# Una deriva que ninguna prueba cubriría: el límite del código sube a 6 solo en el código.
git checkout -q main && git checkout -q -b atajo-limite
sed -i '' 's/MAXIMO_ETIQUETAS = 5;/MAXIMO_ETIQUETAS = 6;/' Servidor.java
git add -A && git commit -q -m "Atajo: el límite sube a 6 en el código"
bash herramientas/deriva.sh . >"$trabajo/salida.txt" && falla "deriva.sh no vio el límite" || ok "deriva.sh detecta sin servidor el límite cambiado solo en el código: $(grep -m1 'límite' "$trabajo/salida.txt" | sed 's/^ *//')"
r=$(aceptacion); [ "${r%/*}" -lt "${r#*/}" ] && ok "y la suite también falla ($r): el escenario 'más de 5' deja de rechazarse" || falla "la suite no vio el límite"
git checkout -q main

echo "== Paso 4: una urgencia (diez minutos)"
git checkout -q -b urgencia
arrancar
nbsp=$(printf 'reunion\xc2\xa0#trabajo')
et=$(curl -s -X POST -H "X-Clave: $CLAVE" -d "$nbsp" localhost:$PUERTO/api/notas | python3 -c "import sys,json;print(json.load(sys.stdin)['etiquetas'])")
echo "        el error de producción: una nota con espacio duro (U+00A0) antes del # -> etiquetas = $et"
[ "$et" = "[]" ] && ok "reproducido: el espacio duro de los teclados móviles impide reconocer la etiqueta" || falla "no se reprodujo"
parar
sed -i '' 's/Pattern.compile("(?<!\\\\S)#/Pattern.compile("(?<![^\\\\s\\\\u00a0])#/' Servidor.java
grep -q 'u00a0' Servidor.java || falla "no se aplicó el arreglo"
git add -A && git commit -q -m "URGENCIA: el espacio duro cuenta como comienzo de palabra (criterio 1)"
arrancar
et=$(curl -s -X POST -H "X-Clave: $CLAVE" -d "$nbsp" localhost:$PUERTO/api/notas | python3 -c "import sys,json;print(json.load(sys.stdin)['etiquetas'])")
parar
[ "$et" = "['trabajo']" ] && ok "arreglado en el código: etiquetas = $et" || falla "el arreglo no funcionó ($et)"
# La urgencia no está cerrada hasta que especificación, prueba y documento coinciden con el código.
if bash herramientas/deriva.sh . >/dev/null; then echo "        deriva.sh no ve el espacio duro (no es un número): sin cierre, la especificación y el código ya difieren"; fi
grep -q 'espacio duro' especificacion.md && falla "la especificación ya lo decía" || ok "la especificación todavía no menciona el espacio duro: la urgencia está abierta"
cat >cambio-006.md <<'MD'
# Cambio 006 (urgencia): el espacio duro cuenta como espacio

## Motivo

Los teclados móviles insertan un espacio duro (U+00A0) tras algunos signos. Una nota `reunión #trabajo` con ese espacio no reconocía la etiqueta.
Arreglado en producción en el commit "URGENCIA". Este documento y los cambios siguientes cierran la urgencia.

## Cambio en la especificación

- Definiciones, "Comienzo de una palabra": también cuenta después de un espacio duro.

## Pruebas afectadas

- Nuevo escenario en `@criterio-1`: texto con espacio duro antes del `#`.

## Riesgo

Ninguno para clientes existentes: se reconocen más etiquetas, no menos.
MD
sed -i '' 's/^- \*\*Comienzo de una palabra\*\*: el principio del texto o justo después de un espacio\./- **Comienzo de una palabra**: el principio del texto o justo después de un espacio (incluido el espacio duro)./' especificacion.md
grep -q 'espacio duro' especificacion.md && ok "especificación actualizada (Definiciones)" || falla "no se actualizó la definición"
python3 - <<'PY'
p='pruebas/src/test/java/etiquetas.feature'; s=open(p).read()
s=s.replace("      | guion y guion bajo | #a-b_c | ['a-b_c'] |","      | guion y guion bajo | #a-b_c | ['a-b_c'] |\n      | espacio duro | reunión #trabajo | ['trabajo'] |")
open(p,'w').write(s)
PY
git add -A && git commit -q -m "Cierre de la urgencia: documento de cambio, especificación y prueba del espacio duro"
verde && ok "con la prueba nueva todo en verde: $(aceptacion)" || falla "la suite no queda en verde tras cerrar la urgencia"
git checkout -q main && git merge -q --no-ff urgencia -m "Urgencia cerrada: espacio duro"
echo "        antes de dar por cerrada la urgencia: (1) arreglo en el código, (2) prueba que lo reproduce, (3) especificación al día, (4) documento de cambio, (5) revisión del arreglo por una segunda persona"

echo "== Paso 5: detección de deriva, hecha por un script"
git checkout -q -b deriva-oculta main
# Dos derivas a la vez: el largo máximo cambia solo en el código, y se agrega "toLowerCase" fuera de la consulta (no se prueba).
sed -i '' 's/LARGO_MAXIMO_ETIQUETA = 30;/LARGO_MAXIMO_ETIQUETA = 40;/' Servidor.java
git add -A && git commit -q -m "Deriva: largo de etiqueta 40 solo en el código"
bash herramientas/deriva.sh . >"$trabajo/salida.txt" && falla "deriva.sh no detectó el largo" || ok "deriva.sh lista: $(grep -m1 'largo' "$trabajo/salida.txt" | sed 's/^ *//')"
r=$(aceptacion); echo "        la suite: $r"
git checkout -q main
echo "        diferencias falsas inventadas por deriva.sh: 0 (en main, sin deriva: $(bash herramientas/deriva.sh . | tail -1 | sed 's/^ *//'))"
bash herramientas/deriva.sh . >/dev/null && ok "en main no hay deriva" || falla "deriva.sh dice que main deriva"

echo "== Paso 6: poda"
antes=$(wc -l <especificacion.md | tr -d ' ')
# Poda real: "Historias de usuario" repite lo que ya dice el Propósito (marcar una nota con etiquetas y ver solo las de una etiqueta).
python3 - <<'PY'
import re
s = open("especificacion.md", encoding="utf-8").read()
nuevo = re.sub(r"## Historias de usuario\n\n(?:- .*\n)+\n", "", s)
assert nuevo != s
open("especificacion.md", "w", encoding="utf-8").write(nuevo)
PY
lineas=$(wc -l <especificacion.md | tr -d ' ')
n=$(sed -n '/^## Criterios de aceptación/,/^## Casos límite/p' especificacion.md | grep -cE '^[0-9]+\. ')
echo "        especificación: $antes líneas antes de podar, $lineas después, $n criterios"
[ "$n" -eq 15 ] && [ "$lineas" -lt "$antes" ] && ok "poda real: se quita 'Historias de usuario' ($((antes - lineas)) líneas) y siguen los 15 criterios" || falla "la poda perdió criterios o no recortó ($lineas líneas, $n criterios)"
bash herramientas/deriva.sh . >/dev/null && ok "después de podar, deriva.sh sigue sin encontrar criterios sin prueba ni deriva" || falla "deriva.sh falla tras la poda"
git add -A && git commit -q -m "Poda: se quita 'Historias de usuario', que repetía el Propósito"
[ "$lineas" -le 100 ] && ok "cabe en dos pantallas (≤ 100 líneas)" || falla "no cabe en dos pantallas ($lineas líneas)"
for f in especificacion.md:especificacion-final.md cambio-005.md:cambio-005.md cambio-006.md:cambio-006.md; do
  diff -q "${f%%:*}" "$aqui/${f##*:}" >/dev/null && ok "${f##*:} coincide con el que genera el recorrido" || falla "${f##*:} difiere del que genera el recorrido"
done
git log --first-parent --format='%h %s' | sed 's/^/        /'
[ "$fallos" -eq 0 ]
