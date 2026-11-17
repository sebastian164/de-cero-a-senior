#!/usr/bin/env bash
# Taller 04: ejecuta los pasos 2 a 10 y escribe resultados/resultados.md. Termina con error si no se cumple un criterio.
# Requiere la base levantada (la levanta el propio script). El servicio externo de pega escucha en 46499.
set -euo pipefail
source "$(dirname "$0")/herramientas/lib.sh"
source "$RAIZ_VER/herramientas/resumen.sh"
export DB_PASSWORD='Tienda2026!'
AQUI="$RAIZ_VER"
OUT="$AQUI/resultados"; rm -rf "$OUT"; mkdir -p "$OUT"
JAR="$AQUI/target/taller-04-1.0.0.jar"
R="$OUT/resultados.md"
DURACION=${DURACION:-20s}
fallos=0
criterio() { if eval "$2"; then echo "   ok    $1"; echo "- [x] $1" >> "$R"; else echo "   FALLA $1"; echo "- [ ] **FALLA** $1" >> "$R"; fallos=$((fallos+1)); fi; }

(cd "$AQUI" && mvn -q -B package -DskipTests)
dc up -d --wait db > /dev/null   # la base del entorno, con max_connections = 30
preparar_tienda

PID=""; EXTERNO=""
java "$RAIZ_VER/herramientas/Externo.java" 46499 50 > "$OUT/externo.log" 2>&1 &
EXTERNO=$!
arrancar() {   # arrancar nombre [args...]
  local nombre=$1; shift
  java ${JVM:-} -jar "$JAR" --server.port=46401 --spring.datasource.url="$DB_URL" "$@" > "$OUT/$nombre.log" 2>&1 &
  PID=$!
  esperar_http 46401 || { echo "no arrancó $nombre"; tail -40 "$OUT/$nombre.log"; exit 1; }
}
parar() { [ -n "$PID" ] && { kill "$PID" 2> /dev/null || true; wait "$PID" 2> /dev/null || true; PID=""; }; return 0; }
trap 'parar; kill $EXTERNO 2> /dev/null || true' EXIT
for _ in $(seq 1 40); do curl -s localhost:46499/cuenta > /dev/null && break; sleep 0.5; done
demora() { curl -s "localhost:46499/demora?ms=$1" > /dev/null; }
cpu_base() { docker stats --no-stream --format '{{.CPUPerc}}' ${CONTENEDOR_DB} | tr -d '%'; }
uso_ms() {   # media y máximo de hikaricp.connections.usage, en ms
  curl -s localhost:46401/actuator/metrics/hikaricp.connections.usage | jq -r \
    '[.measurements[] | {(.statistic): .value}] | add | "\((.TOTAL_TIME / (.COUNT|if .==0 then 1 else . end) * 1000)|round) \((.MAX*1000)|round)"'
}

# escenario nombre url vus [METODO]: carga en segundo plano y, a mitad, una foto de las métricas. Añade una fila a la tabla.
escenario() {
  local nombre=$1 url=$2 vus=$3 metodo=${4:-POST}
  METODO=$metodo K6_URL="$url" VUS="$vus" DURACION="$DURACION" TIMEOUT=60s k6 run -q --summary-export "$OUT/$nombre.json" \
    "$RAIZ_VER/herramientas/carga.js" > "$OUT/$nombre.k6.txt" 2>&1 &
  local k6=$!
  sleep 10
  local activas pendientes cpu hilos
  activas=$(metrica 46401 hikaricp.connections.active); pendientes=$(metrica 46401 hikaricp.connections.pending)
  hilos=$(metrica 46401 jvm.threads.live); cpu=$(cpu_base)
  wait $k6 || true
  read -r rps p95 err n < <(resumen "$OUT/$nombre.json")
  read -r usom usomax < <(uso_ms)
  local timeouts; timeouts=$(metrica 46401 hikaricp.connections.timeout | cut -d. -f1)
  echo "| $nombre | $rps | $p95 | $err | ${activas%.*} | ${pendientes%.*} | $usom / $usomax | $timeouts | $cpu | ${hilos%.*} |" >> "$R"
  echo "$nombre $rps $p95 $err ${activas%.*} ${pendientes%.*} $usom $usomax $timeouts $cpu ${hilos%.*}" >> "$OUT/tabla.tsv"
  echo "   $nombre: $rps rps, p95 $p95 ms, errores $err %, activas ${activas%.*}, pendientes ${pendientes%.*}, uso $usom ms (máx $usomax), CPU base $cpu %"
}
col() { awk -v e="$1" -v c="$2" '$1==e {print $c}' "$OUT/tabla.tsv"; }
cabecera() { { echo; echo "## $1"; echo; echo "| Escenario | peticiones/s | p95 (ms) | errores % | activas | pendientes | uso de la conexión media / máx (ms) | timeouts del pool | CPU de PostgreSQL % | hilos de la JVM |"; echo "|---|---|---|---|---|---|---|---|---|---|"; } >> "$R"; }
: > "$OUT/tabla.tsv"

echo "# Resultados del taller 04 ($(date '+%Y-%m-%d %H:%M'), pool de 5, $DURACION por escenario)" > "$R"
psqlv -q -c "update pedido set estado = 'PENDIENTE', confirmaciones = 0" 2> /dev/null || true

echo "== Pasos 2 y 3: todo en una transacción"
cabecera "Pasos 2 y 3: @Transactional alrededor de todo (v1), 20 usuarios"
arrancar v1-50ms; demora 50;   escenario v1-externo-50ms "http://localhost:46401/v1/pedidos/{id}/confirmar" 20; parar
arrancar v1;      demora 2000; escenario v1-externo-2s   "http://localhost:46401/v1/pedidos/{id}/confirmar" 20
grep -m1 -o "pedidos - Connection is not available, request timed out after [0-9]*ms ([^)]*)" "$OUT/v1.log" > "$OUT/v1-error.txt" || true
parar

echo "== Paso 5: transacciones cortas, mismo pool"
cabecera "Paso 5: transacciones cortas y la llamada fuera (v2), mismo pool de 5"
# Hallazgo: con JPA y open-in-view en su valor por defecto (true) las transacciones cortas NO liberan la conexión: la retiene la vista abierta.
arrancar v2-osiv; demora 2000; escenario v2-osiv-externo-2s "http://localhost:46401/v2/pedidos/{id}/confirmar" 20; parar
arrancar v2-50ms --spring.jpa.open-in-view=false; demora 50;   escenario v2-externo-50ms "http://localhost:46401/v2/pedidos/{id}/confirmar" 20; parar
arrancar v2 --spring.jpa.open-in-view=false;      demora 2000; escenario v2-externo-2s   "http://localhost:46401/v2/pedidos/{id}/confirmar" 20

echo "== Paso 6: el guardado falla después de cobrar"
demora 50
psqlv -q -c "update pedido set estado = 'PENDIENTE' where id = 7"
# el fallo viene de fuera, de la base: una restricción que rechaza confirmar el pedido 7 (el guardado falla DESPUÉS del cobro)
psqlv -q -c "alter table pedido add constraint no_confirmar_el_7 check (id <> 7 or estado <> 'CONFIRMADO') not valid"
antes=$(curl -s localhost:46499/cuenta)
codigo6=$(curl -s -o /dev/null -w '%{http_code}' -X POST "localhost:46401/v2/pedidos/7/confirmar")
psqlv -q -c "alter table pedido drop constraint no_confirmar_el_7"
cobros=$(( $(curl -s localhost:46499/cuenta) - antes ))
estado7=$(psqlv -tAc "select estado from pedido where id = 7")
{ echo; echo "## Paso 6: el guardado falla después de un cobro correcto"; echo
  echo "Respuesta HTTP $codigo6. Cobros recibidos por pagos: $cobros. Estado del pedido 7 en la base: $estado7."; } >> "$R"
criterio "paso 6: queda un cobro hecho y el pedido sin confirmar (inconsistencia a resolver)" '[ "$codigo6" = 500 ] && [ "$cobros" = 1 ] && [ "$estado7" = PENDIENTE ]'
parar

v1_2s_err=$(col v1-externo-2s 4); v2_2s_err=$(col v2-externo-2s 4)
v1_2s_rps=$(col v1-externo-2s 2); v2_2s_rps=$(col v2-externo-2s 2)
criterio "paso 3: con pagos a 2 s y todo en una transacción, el pool de 5 se agota ($v1_2s_err % de errores, $v1_2s_rps rps)" \
  '[ "$(echo "$v1_2s_err > 10" | bc)" = 1 ] && [ "$(col v1-externo-2s 9)" -gt 0 ]'
criterio "paso 3: mientras, la base está ociosa (CPU $(col v1-externo-2s 10) %)" '[ "$(echo "$(col v1-externo-2s 10) < 20" | bc)" = 1 ]'
criterio "paso 3: el uso de la conexión (~$(col v1-externo-2s 7) ms) es el tiempo de pagos, no el de las consultas" '[ "$(col v1-externo-2s 7)" -ge 1900 ]'
criterio "paso 5 (hallazgo): con JPA y open-in-view por defecto, partir la transacción no arregla nada: $(col v2-osiv-externo-2s 4) % de errores, uso $(col v2-osiv-externo-2s 7) ms" \
  '[ "$(echo "$(col v2-osiv-externo-2s 4) > 10" | bc)" = 1 ] && [ "$(col v2-osiv-externo-2s 7)" -ge 1900 ]'
criterio "paso 5: con open-in-view desactivado, el mismo pool de 5 y pagos a 2 s no hay errores ($v2_2s_err %) y se atienden ~10 rps ($v2_2s_rps)" \
  '[ "$v2_2s_err" = 0 ] && [ "$v2_2s_rps" -ge 9 ]'
criterio "paso 5: el uso de la conexión vuelve a milisegundos ($(col v2-externo-2s 7) ms)" '[ "$(col v2-externo-2s 7)" -lt 50 ]'

echo "== Paso 7: la vista abierta"
{ echo; echo "## Paso 7: open-in-view"; echo; echo "| Variante | código HTTP | uso de la conexión media / máx (ms) |"; echo "|---|---|---|"; } >> "$R"
vista() {   # vista clave descripcion ruta [args]
  local clave=$1 etiqueta=$2 ruta=$3; shift 3
  arrancar "vista-$clave" "$@"
  local codigo; codigo=$(curl -s -o "$OUT/vista-$clave.body" -w '%{http_code}' "localhost:46401$ruta")
  read -r m x < <(uso_ms)
  echo "| $etiqueta | $codigo | $m / $x |" >> "$R"
  echo "$etiqueta $codigo $m $x" >> "$OUT/vista.tsv"
  parar
}
: > "$OUT/vista.tsv"
vista oiv-true "open-in-view por defecto (true), /vista" /vista/pedidos/5
vista oiv-false "open-in-view false, /vista" /vista/pedidos/5 --spring.jpa.open-in-view=false
vista oiv-false-corregida "open-in-view false, /vista-corregida" /vista-corregida/pedidos/5 --spring.jpa.open-in-view=false
grep -m1 -o "LazyInitializationException: [^]]*" "$OUT/vista-oiv-false.log" > "$OUT/vista-error.txt" || true
grep -m1 "spring.jpa.open-in-view is enabled by default" "$OUT/vista-oiv-true.log" | sed -E 's/.*WARN/WARN/' > "$OUT/vista-aviso.txt" || true
{ echo; echo "Con open-in-view desactivado, /vista falla con: \`$(cat "$OUT/vista-error.txt")\`"; echo
  echo "Aviso de Spring Boot al arrancar con el valor por defecto: \`$(sed -E 's/.*: (spring.jpa)/\1/' "$OUT/vista-aviso.txt")\`"; } >> "$R"
l1=$(sed -n 1p "$OUT/vista.tsv"); l2=$(sed -n 2p "$OUT/vista.tsv"); l3=$(sed -n 3p "$OUT/vista.tsv")
criterio "paso 7: con open-in-view la conexión se retiene el segundo entero (máximo $(awk '{print $NF}' <<< "$l1") ms)" '[ "$(awk "{print \$NF}" <<< "$l1")" -ge 1000 ]'
criterio "paso 7: sin open-in-view, la relación perezosa rompe: HTTP 500 con LazyInitializationException" '[[ "$l2" == *" 500 "* ]] && [ -s "$OUT/vista-error.txt" ]'
# la primera petición de una JVM recién arrancada carga clases y calienta Hibernate: con la máquina cargada llega a unos cientos de ms, frente a más de 1000 con la vista abierta
criterio "paso 7: el arreglo (lectura con join fetch en transacción de solo lectura) funciona y retiene milisegundos (máximo $(awk '{print $NF}' <<< "$l3") ms)" \
  '[[ "$l3" == *" 200 "* ]] && [ "$(awk "{print \$NF}" <<< "$l3")" -lt 500 ]'

echo "== Paso 8: consulta lenta"
if [ "$(psqlv -tAc "select count(*) from pg_tables where tablename = 'historial'")" = 0 ]; then
  psqlv -q -c "create table historial (id bigint generated always as identity primary key, cliente_id int not null, importe int not null, relleno text not null)" \
    -c "insert into historial (cliente_id, importe, relleno) select (random() * 100000)::int, (random() * 1000)::int, md5(n::text) || md5((n + 1)::text) from generate_series(1, 6000000) n" \
    -c "grant select on historial to tienda" -c "analyze historial"
fi
psqlv -q -c "create index if not exists historial_por_cliente on historial (cliente_id)"
# Sin paralelismo del plan: así la consulta sin índice tarda lo mismo con 2 o con 12 núcleos libres y usa una sola conexión.
psqlv -q -c "alter database tienda set max_parallel_workers_per_gather = 0"
consulta_ms() { psqlv -tAc "explain (analyze, format json) select count(*), sum(importe) from historial where cliente_id = 4242" | jq '.[0]["Execution Time"] | round'; }
con_indice=$(consulta_ms)
cabecera "Paso 8: consulta lenta, 20 usuarios contra /historial"
arrancar historial-con-indice
escenario historial-con-indice "http://localhost:46401/historial/{id}" 20 GET
parar
psqlv -q -c "drop index historial_por_cliente"
arrancar historial-sin-indice
sin_indice=$(consulta_ms)
escenario historial-sin-indice "http://localhost:46401/historial/{id}" 20 GET
psqlv -q -c "create index historial_por_cliente on historial (cliente_id)"
parar
{ echo; echo "La consulta, medida con explain analyze: $con_indice ms con índice, $sin_indice ms sin índice (6 millones de filas)."; } >> "$R"
criterio "paso 8: sin índice la consulta pasa de milisegundos ($con_indice) a más de cien ($sin_indice) y al menos 20 veces más" '[ "$con_indice" -lt 10 ] && [ "$sin_indice" -ge 100 ] && [ "$sin_indice" -ge $(( 20 * (con_indice > 0 ? con_indice : 1) )) ]'
criterio "paso 8: el pool se ve igual que con la llamada externa (activas al máximo, uso largo: $(col historial-sin-indice 7) ms) pero la CPU de la base sube ($(col historial-sin-indice 10) %)" \
  '[ "$(col historial-sin-indice 5)" = 5 ] && [ "$(col historial-sin-indice 7)" -ge 150 ] && [ "$(echo "$(col historial-sin-indice 10) > 80" | bc)" = 1 ]'

echo "== Paso 9: bloqueos"
arrancar bloqueos
curl -s -o /dev/null -X POST "localhost:46401/bloqueo/1?pausa=6000" &
LENTA=$!
sleep 0.5
BLOQ=(); : > "$OUT/bloqueo-codigos.txt"
for _ in $(seq 1 8); do curl -s -o /dev/null -w '%{http_code} %{time_total}\n' -X POST "localhost:46401/bloqueo/1" >> "$OUT/bloqueo-codigos.txt" & BLOQ+=($!); done
sleep 2
activas9=$(metrica 46401 hikaricp.connections.active | cut -d. -f1); pendientes9=$(metrica 46401 hikaricp.connections.pending | cut -d. -f1)
esperando=$(psqlv -tAc "select count(*) from pg_stat_activity where usename = 'tienda' and wait_event_type = 'Lock'")
cpu9=$(cpu_base)
otra=$(curl -s -o /dev/null -w '%{http_code} %{time_total}' "localhost:46401/historial/1")
wait $LENTA "${BLOQ[@]}" || true
parar
{ echo; echo "## Paso 9: una transacción retiene la fila 1 durante 6 s y llegan 8 actualizaciones más de la misma fila"; echo
  echo "- A los 2 s: activas $activas9, pendientes $pendientes9, sesiones esperando un bloqueo en PostgreSQL: $esperando, CPU de la base $cpu9 %."
  echo "- Mientras, una petición a otro extremo (/historial/1): \`$otra\` (código y segundos)."
  echo "- Códigos y tiempos de las 8: $(sort "$OUT/bloqueo-codigos.txt" | awk '{print $1 " " $2 "s"}' | tr '\n' ',' | sed 's/,$//')"; } >> "$R"
criterio "paso 9: las que esperan el bloqueo retienen su conexión: pool lleno ($activas9) y 4 sesiones con wait_event_type = Lock ($esperando)" \
  '[ "$activas9" = 5 ] && [ "$esperando" = 4 ]'
criterio "paso 9: otros extremos se quedan sin conexión aunque la base no trabaja (otra petición: $otra)" '[[ "$otra" == 500* ]] || [ "${pendientes9:-0}" -gt 0 ]'

echo "== Paso 10: hilos virtuales"
cabecera "Paso 10: pagos a 2 s, todo en una transacción (v1), hilos de plataforma frente a hilos virtuales"
demora 2000
arrancar plataforma
escenario v1-2s-plataforma-200u "http://localhost:46401/v1/pedidos/{id}/confirmar" 200
parar
arrancar virtuales --spring.threads.virtual.enabled=true
escenario v1-2s-virtuales-200u "http://localhost:46401/v1/pedidos/{id}/confirmar" 200
parar
criterio "paso 10: con hilos virtuales hay menos hilos de plataforma ($(col v1-2s-plataforma-200u 11) -> $(col v1-2s-virtuales-200u 11)) pero los mismos errores de pool ($(col v1-2s-plataforma-200u 4) % / $(col v1-2s-virtuales-200u 4) %)" \
  '[ "$(col v1-2s-virtuales-200u 11)" -lt "$(col v1-2s-plataforma-200u 11)" ] && [ "$(echo "$(col v1-2s-virtuales-200u 4) > 50" | bc)" = 1 ]'

echo "== Sesión 05, arranque sin base: la aplicación del taller (JPA y spring.sql.init) con la base apagada"
{ echo; echo "## Arrancar sin base con JPA (sesión 05, 'Cuando la base se cae y vuelve')"; echo; } >> "$R"
for variante in "" "--spring.sql.init.mode=never"; do
  java -jar "$JAR" --server.port=46404 --spring.datasource.url=jdbc:postgresql://localhost:46999/tienda $variante > "$OUT/sinbase.log" 2>&1 &
  SB=$!; for _ in $(seq 1 120); do kill -0 "$SB" 2> /dev/null || break; sleep 0.5; done
  if kill -0 "$SB" 2> /dev/null; then kill "$SB"; vivo=si; else vivo=no; fi; salio=0; wait "$SB" 2> /dev/null || salio=$?
  criterio "arrancar sin base con JPA${variante:+ y $variante}: el proceso termina con error (sigue vivo: $vivo; código $salio) porque algo toma una conexión al arrancar ($(grep -o -m1 'Unable to obtain isolated JDBC connection\|dataSourceScriptDatabaseInitializer' "$OUT/sinbase.log" | head -1))" \
    '[ "$vivo" = no ] && [ "$salio" -ne 0 ] && grep -q "Connection to localhost:46999 refused" "$OUT/sinbase.log"'
done

echo; grep -E "^- \[" "$R"
[ "$fallos" -eq 0 ] || { echo "taller 04: $fallos criterios fallaron"; exit 1; }
echo "taller 04: todo correcto"
