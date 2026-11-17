#!/usr/bin/env bash
# Taller 06: pasos 3, 4 y 9 (pruebas automáticas) y pasos 5 a 8 (mediciones con k6) sobre el caso de uso "crear pedido" del servicio de pedidos
# del módulo hexagonal (solución del taller 08, compilada desde su sitio). Escribe resultados/resultados.md.
# Requiere la base levantada (la levanta el propio script). El servicio de notificaciones de pega escucha en 46699.
set -euo pipefail
source "$(dirname "$0")/herramientas/lib.sh"
AQUI="$RAIZ_VER"
OUT="$AQUI/resultados"; rm -rf "$OUT"; mkdir -p "$OUT"
R="$OUT/resultados.md"
DURACION=${DURACION:-20s}
fallos=0
criterio() { if eval "$2"; then echo "   ok    $1"; echo "- [x] $1" >> "$R"; else echo "   FALLA $1"; echo "- [ ] **FALLA** $1" >> "$R"; fallos=$((fallos+1)); fi; }
export DB_USUARIO=tienda DB_PASSWORD='Tienda2026!'
# Un esquema propio (t06): otros talleres crean en la misma base tablas "pedido" y "evento_pendiente" con otras columnas.
export DB_URL="$DB_URL?currentSchema=t06"
psqlt() { dc exec -T -e PGPASSWORD="$DB_PASSWORD" -e PGOPTIONS=-csearch_path=t06 db psql -h localhost -U tienda -d tienda -v ON_ERROR_STOP=1 "$@"; }

(cd "$AQUI" && mvn -q -B package -DskipTests)
dc up -d --wait db > /dev/null   # la base del entorno, con max_connections = 30
preparar_tienda
psqlv -q -c "drop schema if exists t06 cascade" -c "create schema t06 authorization tienda"
HEX="$(cd "$RAIZ_VER/../../../../hexagonal" && pwd)"
SCHEMA="$HEX/07-persistencia-con-orm/taller/solucion-de-referencia/src/main/resources/schema.sql"
psqlt -q < "$SCHEMA"
PEDIDOS="$HEX/08-pruebas-y-limites/taller/solucion-de-referencia"
PRODUCTOS=500

echo "# Resultados del taller 06 ($(date '+%Y-%m-%d %H:%M'), $DURACION por medida)" > "$R"

echo "== Pasos 3, 4 y 9: pruebas automáticas (conexiones falsas, contratos con PostgreSQL real, bandeja, getConnection)"
if (cd "$AQUI" && mvn -q -B test > "$OUT/mvn-test.txt" 2>&1); then pruebas=ok; else pruebas=falla; tail -40 "$OUT/mvn-test.txt"; fi
{ echo; echo "## Pasos 3, 4 y 9: pruebas"; echo; echo '```'; grep -h "Tests run" "$AQUI"/target/surefire-reports/*.txt; echo '```'; } >> "$R"
criterio "pasos 3, 4 y 9: las pruebas pasan (adaptador con conexiones falsas; contratos de pedidos e inventario con PostgreSQL; crear pedido con los adaptadores JDBC; entrega fallida a la bandeja; anidada con pool de 1; solo la unidad de trabajo llama a getConnection)" '[ "$pruebas" = ok ]'
( cd "$PEDIDOS/src/tienda" && find dominio aplicacion -name '*.java' | sort | xargs shasum -a 256 ) > "$OUT/huellas-dominio-y-aplicacion.txt"
criterio "el taller no añade ni copia archivos en tienda.dominio ni tienda.aplicacion: se compilan desde la solución hexagonal (ArquitecturaDelPoolTest; $(wc -l < "$OUT/huellas-dominio-y-aplicacion.txt" | tr -d ' ') archivos, huellas en resultados/)" \
  '[ "$pruebas" = ok ] && [ -s "$OUT/huellas-dominio-y-aplicacion.txt" ]'

java "$RAIZ_VER/herramientas/Externo.java" 46699 300 > "$OUT/externo.log" 2>&1 &
EXTERNO=$!
PID=""; MUESTREO=""
parar() { [ -n "$MUESTREO" ] && { kill "$MUESTREO" 2> /dev/null || true; wait "$MUESTREO" 2> /dev/null || true; MUESTREO=""; }
          [ -n "$PID" ] && { kill "$PID" 2> /dev/null || true; wait "$PID" 2> /dev/null || true; PID=""; }; return 0; }
trap 'parar; kill $EXTERNO 2> /dev/null || true; psqlv -q -c "drop schema if exists t06 cascade" > /dev/null 2>&1 || true' EXIT
for _ in $(seq 1 40); do curl -s localhost:46699/cuenta > /dev/null && break; sleep 0.5; done
demora() { curl -s "localhost:46699/demora?ms=$1" > /dev/null; }
CP="$AQUI/target/classes:$(cat "$AQUI/target/classpath.txt")"

# corrida nombre pool eventos fugas_ms rate: arranca la tienda, carga y deja el resumen en $OUT/nombre.json y el estado final en $OUT/nombre.pool
corrida() {
  local nombre=$1 pool=$2 eventos=$3 fugas=$4 rate=$5
  psqlt -q -c "truncate pedido, linea_de_pedido, peticion_procesada, evento_pendiente cascade" \
    -c "insert into producto (id, precio_centavos) select 'p-' || lpad(n::text, 4, '0'), 10000 from generate_series(0, $PRODUCTOS - 1) n on conflict do nothing" \
    -c "insert into existencias (producto, unidades, version) select 'p-' || lpad(n::text, 4, '0'), 1000000000, 0 from generate_series(0, $PRODUCTOS - 1) n on conflict (producto) do update set unidades = 1000000000, version = 0"
  DB_URL="$DB_URL" POOL=$pool PUERTO=46601 NOTIFICACIONES=http://localhost:46699 EVENTOS=$eventos FUGAS_MS=$fugas \
    java -cp "$CP" tienda.composicion.TiendaConPool > "$OUT/$nombre.log" 2>&1 &
  PID=$!
  for _ in $(seq 1 60); do curl -sf localhost:46601/pool > /dev/null && break; sleep 0.5; done
  ( : > "$OUT/$nombre.tsv"; while true; do curl -s localhost:46601/pool | jq -r '[.activas,.pendientes]|@tsv' >> "$OUT/$nombre.tsv"; sleep 0.5; done ) &
  MUESTREO=$!
  BASE=http://localhost:46601 PRODUCTOS=$PRODUCTOS RATE=$rate DURACION="$DURACION" k6 run -q --summary-export "$OUT/$nombre.json" "$RAIZ_VER/herramientas/pedidos.js" > "$OUT/$nombre.k6.txt" 2>&1 || true
  sleep 1
  curl -s localhost:46601/pool > "$OUT/$nombre.pool"
  kill "$MUESTREO" 2> /dev/null || true; wait "$MUESTREO" 2> /dev/null || true; MUESTREO=""
  if [ "$eventos" = bandeja ]; then sleep 3; curl -s localhost:46601/pool > "$OUT/$nombre.pool"; fi
  if [ "$eventos" = bandeja ]; then PENDIENTES_BANDEJA=$(psqlt -tAc "select count(*) from evento_pendiente"); fi
  kill "$PID" 2> /dev/null || true; wait "$PID" 2> /dev/null || true; PID=""
}
dato() { jq -r "$2" "$OUT/$1.json"; }
fila() {   # fila nombre: una línea de tabla con lo medido
  local n=$1 ok mal p95 maxact maxpend
  ok=$(dato "$n" '.metrics.pedido_ok.count // 0'); mal=$(dato "$n" '.metrics.pedido_error.count // 0')
  p95=$(dato "$n" '.metrics.pedido_ms["p(95)"]|.*10|round/10')
  maxact=$(awk 'BEGIN{m=0} $1>m{m=$1} END{print m}' "$OUT/$n.tsv"); maxpend=$(awk 'BEGIN{m=0} $2>m{m=$2} END{print m}' "$OUT/$n.tsv")
  echo "| $n | $ok | $mal | $p95 | $maxact | $maxpend | $(jq -r '[.usoMedioMs, .usoMaxMs, .esperaMediaMs, .timeouts]|map(tostring)|join(" / ")' "$OUT/$n.pool") |"
  echo "$n $ok $mal $p95 $maxact $maxpend $(jq -r '[.usoMedioMs,.usoMaxMs,.esperaMediaMs,.timeouts,.entregados]|join(" ")' "$OUT/$n.pool")" >> "$OUT/tabla.tsv"
}
tabla() { { echo; echo "## $1"; echo; echo "| Medida | pedidos bien | con error | p95 (ms) | activas (máx) | en espera (máx) | uso medio / máx (ms) / espera media (ms) / timeouts |"; echo "|---|---|---|---|---|---|---|"; } >> "$R"; }
col() { awk -v e="$1" -v c="$2" '$1==e {print $c}' "$OUT/tabla.tsv"; }
: > "$OUT/tabla.tsv"

echo "== Paso 5: cuánto retiene 'crear pedido' y el tamaño que sale de la cuenta"
demora 0
corrida retencion 10 ninguno 0 100
tabla "Paso 5a: retención de 'crear pedido' (caso de uso del servicio de pedidos del módulo hexagonal), pool de 10, 100 peticiones/s, sin servicio lento"
fila retencion >> "$R"
uso=$(col retencion 7)
OBJETIVO=500
# La cuenta de la sesión 02: conexiones = peticiones por segundo x segundos de retención, con margen del 50 % y mínimo de 2.
POOL=$(awk -v u="$uso" -v o=$OBJETIVO 'BEGIN{n=o*u/1000*1.5; p=int(n); if (p<n) p++; if (p<2) p=2; print p}')
{ echo; echo "Retención media: $uso ms. Objetivo: $OBJETIVO pedidos por segundo."
  echo "Cuenta: $OBJETIVO x $uso / 1000 = $(awk -v u="$uso" -v o=$OBJETIVO 'BEGIN{printf "%.2f", o*u/1000}') conexiones ocupadas en promedio; con un margen del 50 % y redondeando hacia arriba, **pool de $POOL**."; } >> "$R"
corrida dimensionado $POOL ninguno 0 $OBJETIVO
# con la máquina saturada por otros procesos un pool de 2 al límite puede sufrir una parada puntual (visto 1 vez en 6): se repite una vez
if [ "$(jq -r '.metrics.pedido_error.count // 0' "$OUT/dimensionado.json")" != 0 ]; then sleep 10; corrida dimensionado $POOL ninguno 0 $OBJETIVO; fi
tabla "Paso 5b: el pool de la cuenta ($POOL) con $OBJETIVO peticiones/s"
fila dimensionado >> "$R"
criterio "paso 5: el pool de $POOL, sacado de la cuenta, atiende $OBJETIVO/s sin errores ($(col dimensionado 3) con error)" '[ "$(col dimensionado 3)" = 0 ] && [ "$(col dimensionado 2)" -gt $((OBJETIVO * ${DURACION%s} * 9 / 10)) ]'
criterio "paso 5: el pool de $POOL es pequeño frente al límite de la base (30) y no se supera ni hay esperas agotadas (activas máx $(col dimensionado 5), timeouts $(col dimensionado 10))" '[ "$POOL" -le 12 ] && [ "$(col dimensionado 5)" -le "$POOL" ] && [ "$(col dimensionado 10)" = 0 ]'

echo "== Paso 6: el defecto, un servicio de 300 ms dentro de la unidad de trabajo"
demora 300
corrida defecto $POOL directo 0 $OBJETIVO
tabla "Paso 6: eventos directos al servicio de 300 ms, pool $POOL, connectionTimeout 1000 ms, $OBJETIVO peticiones/s"
fila defecto >> "$R"
# El uso medio se diluye con las peticiones de preparación y las lecturas que sí son cortas: la evidencia de la retención es el máximo.
criterio "paso 6: la retención pasa a ~300 ms (máximo $(col defecto 8) ms; medio $(col defecto 7) ms) y el pool se agota: $(col defecto 3) errores de $(( $(col defecto 2) + $(col defecto 3) ))" \
  '[ "$(printf "%.0f" "$(col defecto 8)")" -ge 280 ] && [ "$(col defecto 3)" -gt 0 ] && [ "$(col defecto 5)" -ge "$POOL" ] && [ "$(col defecto 6)" -gt 0 ] && [ "$(col defecto 10)" -gt 0 ]'

echo "== Paso 7: leakDetectionThreshold = 2000 ms (HikariCP desactiva los umbrales menores de 2000 ms; comprobado con la 7.1.0) con el servicio a 2,5 s"
demora 2500
corrida fugas $POOL directo 2000 5
demora 300
grep -m1 -A14 "Connection leak detection triggered" "$OUT/fugas.log" | cut -c1-220 > "$OUT/aviso-de-fuga.txt" || true
primera=$(grep -m1 -E "^[[:space:]]+at tienda\.(aplicacion|adaptadores)" "$OUT/aviso-de-fuga.txt" | sed 's/^[[:space:]]*//' || true)
segunda=$(grep -E "^[[:space:]]+at tienda\." "$OUT/aviso-de-fuga.txt" | sed -n 2p | sed 's/^[[:space:]]*//' || true)
{ echo; echo "## Paso 7: leakDetectionThreshold = 2000 ms con el servicio de 2,5 s (5 peticiones/s): HikariCP desactiva los umbrales menores de 2000 ms (comprobado con la 7.1.0)"; echo; echo '```'; cat "$OUT/aviso-de-fuga.txt"; echo '```'
  echo; echo "Primeras líneas de la tienda en la traza: \`$primera\` y \`$segunda\`."; } >> "$R"
criterio "paso 7: el aviso sale y la traza muestra el caso de uso que tomó la conexión ($segunda)" \
  'grep -q "Connection leak detection triggered" "$OUT/aviso-de-fuga.txt" && grep -q "UnidadDeTrabajoJdbc.ejecutar" "$OUT/aviso-de-fuga.txt" && grep -q "ServicioDePedidos.crear" "$OUT/aviso-de-fuga.txt"'

echo "== Paso 8: el arreglo, la bandeja de salida (mismo pool, mismo connectionTimeout)"
CALLS_ANTES=$(curl -s localhost:46699/cuenta)
corrida arreglo $POOL bandeja 0 $OBJETIVO
tabla "Paso 8: eventos por la bandeja de salida, mismo pool ($POOL) y mismo connectionTimeout (1000 ms), $OBJETIVO peticiones/s"
fila arreglo >> "$R"
pendientes_bandeja=$PENDIENTES_BANDEJA
llamadas=$(( $(curl -s localhost:46699/cuenta) - CALLS_ANTES ))   # lo que el servicio lento recibió de verdad durante la corrida
{ echo; echo "Eventos entregados según el mensajero: $(col arreglo 11). Llamadas recibidas por el servicio lento: $llamadas. Quedan en la bandeja al terminar: $pendientes_bandeja."; } >> "$R"
criterio "paso 8: sin errores ($(col arreglo 3)) con el mismo pool y la misma espera, y la retención vuelve a milisegundos ($(col arreglo 7) ms)" \
  '[ "$(col arreglo 3)" = 0 ] && [ "$(printf "%.0f" "$(col arreglo 7)")" -lt 50 ]'
criterio "paso 8: nada se perdió: el servicio lento recibió $llamadas llamadas para $(col arreglo 2) pedidos creados (mensajero: $(col arreglo 11); bandeja pendiente $pendientes_bandeja)" \
  '[ "$llamadas" = "$(col arreglo 2)" ] && [ "$(col arreglo 11)" = "$(col arreglo 2)" ] && [ "$pendientes_bandeja" = 0 ]'
parar

echo; grep -E "^- \[" "$R"
[ "$fallos" -eq 0 ] || { echo "taller 06: $fallos criterios fallaron"; exit 1; }
echo "taller 06: todo correcto"
