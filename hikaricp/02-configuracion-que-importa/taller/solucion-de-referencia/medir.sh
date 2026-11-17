#!/usr/bin/env bash
# Taller 02: ejecuta los pasos 1 a 10 y escribe resultados/resultados.md. Termina con error si no se cumple un criterio.
# Variables: DURACION (por defecto 15s por punto de medida; el enunciado pide un minuto).
# Dura entre 12 y 30 minutos según la máquina. Con RAPIDO=1 (unos 5 minutos) mide 5 s por punto y solo con tres tamaños de pool
# y se salta los pasos 7 y 8, que esperan más de un minuto cada uno.
set -euo pipefail
source "$(dirname "$0")/herramientas/lib.sh"
source "$RAIZ_VER/herramientas/resumen.sh"
AQUI="$RAIZ_VER"
OUT="$AQUI/resultados"; mkdir -p "$OUT"
JAR="$AQUI/target/taller-02-1.0.0.jar"
DURACION=${DURACION:-15s}
TAMANOS="2 5 10 20 28"
if [ -n "${RAPIDO:-}" ]; then DURACION=${DURACION_RAPIDA:-5s}; TAMANOS="2 5 28"; fi
export DB_PASSWORD='Tienda2026!'
R="$OUT/resultados.md"
fallos=0
criterio() { if eval "$2"; then echo "   ok    $1"; echo "- [x] $1" >> "$R"; else echo "   FALLA $1"; echo "- [ ] **FALLA** $1" >> "$R"; fallos=$((fallos+1)); fi; }

(cd "$AQUI" && mvn -q -B package -DskipTests)
dc up -d --wait db > /dev/null   # la base del entorno, con max_connections = 30
psqlv -q -c "do \$\$ begin if not exists (select from pg_roles where rolname = 'tienda') then create role tienda login password 'Tienda2026!'; end if; end \$\$;"

PIDS=()
arrancar() {   # arrancar nombre puerto [args...]
  local nombre=$1 puerto=$2; shift 2
  java ${JVM:-} -jar "$JAR" --server.port="$puerto" --spring.datasource.url="$DB_URL" "$@" > "$OUT/$nombre.log" 2>&1 &
  PIDS+=($!)
  for _ in $(seq 1 120); do [ "$(curl -s -o /dev/null -w '%{http_code}' "localhost:$puerto/actuator/health")" != "000" ] && return 0; sleep 0.5; done
  echo "no arrancó $nombre"; tail -50 "$OUT/$nombre.log"; exit 1
}
parar_todo() { for p in ${PIDS[@]+"${PIDS[@]}"}; do kill "$p" 2> /dev/null || true; wait "$p" 2> /dev/null || true; done; PIDS=(); }
trap 'parar_todo; docker update --cpus 0 ${CONTENEDOR_DB} > /dev/null 2>&1 || true' EXIT
calentar() { for _ in $(seq 1 20); do curl -s -o /dev/null "localhost:$1/consulta"; done; }
carga() {     # carga nombre url vus duracion
  K6_URL="$2" VUS="$3" DURACION="$4" TIMEOUT=90s k6 run -q --summary-export "$OUT/$1.json" "$RAIZ_VER/herramientas/carga.js" > "$OUT/$1.k6.txt" 2>&1 || true
}
metrica() { curl -s "localhost:$1/actuator/metrics/$2" | jq -r '.measurements[0].value'; }

echo "# Resultados del taller 02 ($(date '+%Y-%m-%d %H:%M'), $DURACION por medida, 50 usuarios)" > "$R"

echo "== Paso 1: valores por defecto"
arrancar defecto 46201
calentar 46201
{ echo; echo "## Paso 1: la configuración por defecto, sin leer la documentación"; echo
  echo "Del registro, con \`logging.level.com.zaxxer.hikari.HikariConfig: debug\`:"; echo; echo '```'
  grep -E "HikariConfig +: (maximumPoolSize|minimumIdle|connectionTimeout|maxLifetime|keepaliveTime|idleTimeout|validationTimeout|leakDetectionThreshold|poolName)" "$OUT/defecto.log" | sed -E 's/.*HikariConfig +: //'
  echo '```'; echo
  echo "De las métricas: hikaricp.connections.max = $(metrica 46201 hikaricp.connections.max), hikaricp.connections.min = $(metrica 46201 hikaricp.connections.min)"; } >> "$R"
criterio "paso 1: el registro muestra maximumPoolSize 10 y connectionTimeout 30000" \
  'grep -q "maximumPoolSize.*10$" "$OUT/defecto.log" && grep -q "connectionTimeout.*30000$" "$OUT/defecto.log"'

echo "== Paso 2: 50 usuarios con la configuración por defecto"
carga paso2 "http://localhost:46201/consulta" 50 "$DURACION"
read -r rps p95 err n < <(resumen "$OUT/paso2.json")
{ echo; echo "## Paso 2: 50 usuarios, configuración por defecto (pool de 10), /consulta con pg_sleep(0.02)"; echo
  echo "$rps peticiones/s, p95 $p95 ms, errores $err %, $n peticiones."; } >> "$R"
parar_todo

barrido() {   # barrido etiqueta ruta
  local etiqueta=$1 ruta=$2
  { echo; echo "### $etiqueta"; echo; echo "| maximum-pool-size | peticiones/s | p95 (ms) | errores % |"; echo "|---|---|---|---|"; } >> "$R"
  for t in $TAMANOS; do
    arrancar "pool$t" 46202 --spring.datasource.hikari.maximum-pool-size=$t
    calentar 46202
    carga "$etiqueta-$t" "http://localhost:46202$ruta" 50 "$DURACION"
    read -r rps p95 err n < <(resumen "$OUT/$etiqueta-$t.json")
    echo "| $t | $rps | $p95 | $err |" >> "$R"
    echo "$etiqueta $t $rps $p95" >> "$OUT/barrido.tsv"
    echo "   $etiqueta pool=$t: $rps rps, p95 $p95 ms"
    parar_todo
  done
}
: > "$OUT/barrido.tsv"
NUCLEOS=$(dc exec -T db nproc)
echo "== Paso 3: barrido de tamaños, base con $NUCLEOS núcleos"
{ echo; echo "## Paso 3: barrido de maximum-pool-size, 50 usuarios, base con $NUCLEOS núcleos"; } >> "$R"
barrido "sleep-${NUCLEOS}nucleos" /consulta
barrido "cpu-${NUCLEOS}nucleos" /cpu

echo "== Paso 5: la base limitada a un núcleo"
docker update --cpus 1 ${CONTENEDOR_DB} > /dev/null
{ echo; echo "## Paso 5: la base limitada a 1 núcleo (docker update --cpus 1)"; } >> "$R"
barrido "cpu-1nucleo" /cpu
barrido "sleep-1nucleo" /consulta
docker update --cpus 0 ${CONTENEDOR_DB} > /dev/null
python3 -I "$AQUI/grafica.py" "$OUT/barrido.tsv" "$OUT/grafica.svg"
{ echo; echo "Gráfica: [grafica.svg](grafica.svg)"; echo
  echo "## Paso 4: la fórmula (núcleos x 2) + discos efectivos"; echo
  echo "- Con $NUCLEOS núcleos: $((NUCLEOS * 2 + 1)) conexiones. Con 1 núcleo: 3."; } >> "$R"
cpu1_5=$(awk '$1=="cpu-1nucleo" && $2==5 {print $3}' "$OUT/barrido.tsv")
cpu1_28=$(awk '$1=="cpu-1nucleo" && $2==28 {print $3}' "$OUT/barrido.tsv")
cpu1_2=$(awk '$1=="cpu-1nucleo" && $2==2 {print $3}' "$OUT/barrido.tsv")
p95_5=$(awk '$1=="cpu-1nucleo" && $2==5 {print $4}' "$OUT/barrido.tsv")
p95_28=$(awk '$1=="cpu-1nucleo" && $2==28 {print $4}' "$OUT/barrido.tsv")
criterio "pasos 3 y 5: con trabajo de CPU en 1 núcleo, pasar de 5 a 28 conexiones no sube el rendimiento ($cpu1_5 -> $cpu1_28 rps)" \
  '[ "$cpu1_28" -le $((cpu1_5 * 110 / 100)) ]'
criterio "pasos 3 y 5: y con 2 conexiones ya se está cerca del máximo ($cpu1_2 rps)" '[ "$cpu1_2" -ge $((cpu1_28 * 80 / 100)) ]'
s2=$(awk '$1 ~ /^sleep-[0-9]+nucleos$/ && $2==2 {print $3}' "$OUT/barrido.tsv"); s28=$(awk '$1 ~ /^sleep-[0-9]+nucleos$/ && $2==28 {print $3}' "$OUT/barrido.tsv")
criterio "paso 3: con pg_sleep, que no gasta CPU, el rendimiento sí crece con el pool ($s2 -> $s28 rps): esa consulta no sirve para ver el límite" \
  '[ "$s28" -gt $((s2 * 5)) ]'

echo "== Paso 6: tres instancias con un pool de 15"
{ echo; echo "## Paso 6: tres instancias"; echo; echo "| Instancias x pool | Total pedido | peticiones/s | p95 (ms) | errores % |"; echo "|---|---|---|---|---|"; } >> "$R"
for i in 1 2 3; do arrancar "tres-15-$i" 4621$i --spring.datasource.hikari.maximum-pool-size=15 --spring.datasource.hikari.connection-timeout=5000; done
sleep 3
carga tres-15 "http://localhost:46211/consulta,http://localhost:46212/consulta,http://localhost:46213/consulta" 50 "$DURACION"
read -r rps p95 err n < <(resumen "$OUT/tres-15.json"); err15=$err
echo "| 3 x 15 | 45 (la aplicación puede usar 27) | $rps | $p95 | $err |" >> "$R"
conexiones15=$(psqlv -tAc "select count(*) from pg_stat_activity where usename = 'tienda'")
parar_todo
# Se repite una vez si falla: con la máquina saturada por otros procesos Docker a veces corta las conexiones a mitad de la medida (una vez se vio
# "This connection has been closed" en el 99 % de las peticiones, sin relación con la cuenta que se comprueba).
for intento in 1 2; do
  for i in 1 2 3 4; do arrancar "tres-6-$i" 4622$i --spring.config.additional-location="file:$AQUI/config-final.yaml"; done
  sleep 2
  carga tres-6 "http://localhost:46221/consulta,http://localhost:46222/consulta,http://localhost:46223/consulta" 50 "$DURACION"
  read -r rps p95 err n < <(resumen "$OUT/tres-6.json"); err6=$err
  conexiones6=$(psqlv -tAc "select count(*) from pg_stat_activity where usename = 'tienda'")
  [ "$err6" = "0" ] && break
  echo "   (intento $intento con errores: $err6 %)"; parar_todo
done
echo "| 3 x 6 (config-final.yaml), con una 4ª instancia arrancada como en un despliegue | 24 | $rps | $p95 | $err |" >> "$R"
parar_todo
{ echo; echo "Conexiones de la aplicación en la base: $conexiones15 con 3 x 15; $conexiones6 con 4 x 6."; echo
  echo "El error con 3 x 15 (de tres-15-*.log), en la instancia que se quedó sin conexiones:"; echo; echo '```'
  grep -h -m1 "Request processing failed" "$OUT"/tres-15-*.log | sed -E 's/^.*(Request processing failed)/\1/' | cut -c1-300 | head -1 || true
  grep -h -m1 "Caused by: org.postgresql.util.PSQLException" "$OUT"/tres-15-*.log | head -1 || true
  echo '```'; } >> "$R"
criterio "paso 6: con 3 x 15 hay errores de conexión ($err15 %)" '[ "$(echo "$err15 > 0" | bc)" = 1 ]'
criterio "paso 6: el error lo origina PostgreSQL (FATAL: remaining connection slots are reserved...) y Spring lo envuelve en CannotGetJdbcConnectionException" \
  'grep -q "CannotGetJdbcConnectionException" "$OUT"/tres-15-*.log && grep -q "remaining connection slots are reserved" "$OUT"/tres-15-*.log'
criterio "paso 6: tres instancias (y una cuarta de despliegue) conviven sin errores con la cuenta de config-final.yaml" '[ "$err6" = "0" ]'

if [ -z "${RAPIDO:-}" ]; then
caida() {   # caida etiqueta timeout_ms
  arrancar "caida-$1" 46231 --spring.datasource.hikari.connection-timeout=$2
  calentar 46231
  K6_URL=http://localhost:46231/consulta VUS=50 DURACION=60s TIMEOUT=90s k6 run -q --summary-export "$OUT/caida-$1.json" "$RAIZ_VER/herramientas/carga.js" > "$OUT/caida-$1.k6.txt" 2>&1 &
  local k6=$!
  sleep 5; dc stop db > /dev/null 2>&1; sleep 8
  local hilos pendientes
  hilos=$(metrica 46231 tomcat.threads.busy); pendientes=$(metrica 46231 hikaricp.connections.pending)
  sleep 27; dc start db > /dev/null 2>&1; dc up -d --wait > /dev/null 2>&1
  wait $k6 || true
  local maxfallo
  maxfallo=$(jq -r '.metrics["http_req_duration{expected_response:false}"].max // 0 | floor' "$OUT/caida-$1.json")
  echo "| $2 | $hilos | $pendientes | $maxfallo | $(resumen "$OUT/caida-$1.json" | awk '{print $3}') |" >> "$R"
  echo "$1 $hilos $maxfallo" >> "$OUT/caida.tsv"
  parar_todo
}
echo "== Paso 7: la base se cae 35 s durante la carga"
: > "$OUT/caida.tsv"
{ echo; echo "## Paso 7: la base se detiene 35 s con 50 usuarios"; echo
  echo "| connection-timeout (ms) | hilos de Tomcat ocupados durante la caída | hilos esperando conexión | duración máxima de una petición fallida (ms) | errores % |"; echo "|---|---|---|---|---|"; } >> "$R"
caida 30s 30000
caida 2s 2000
m30=$(awk '$1=="30s"{print $3}' "$OUT/caida.tsv"); m2=$(awk '$1=="2s"{print $3}' "$OUT/caida.tsv")
criterio "paso 7: con 30 s, las peticiones fallan tras unos 30 s ($m30 ms); con 2 s, tras unos 2 s ($m2 ms)" \
  '[ "$m30" -ge 29000 ] && [ "$m2" -le 6000 ]'

echo "== Paso 8: PostgreSQL cierra las sesiones ociosas al minuto"
psqlv -q -c "alter system set idle_session_timeout = '60s'" -c "select pg_reload_conf()" > /dev/null
ocioso() {  # ocioso etiqueta [args]
  local etiqueta=$1; shift
  arrancar "ocioso-$etiqueta" 46241 "$@"
  calentar 46241
  sleep 75
  local codigos="" tiempos=""
  for _ in $(seq 1 12); do
    read -r c t < <(curl -s -o /dev/null -w '%{http_code} %{time_total}\n' localhost:46241/consulta)
    codigos="$codigos $c"; tiempos="$tiempos $(printf '%.0f' "$(echo "$t * 1000" | bc)")"
  done
  local avisos
  avisos=$(grep -c "Failed to validate connection" "$OUT/ocioso-$etiqueta.log" || true)
  echo "| $etiqueta | $codigos | $tiempos | $avisos |" >> "$R"
  echo "$etiqueta $avisos" >> "$OUT/ocioso.tsv"
  parar_todo
}
: > "$OUT/ocioso.tsv"
{ echo; echo "## Paso 8: idle_session_timeout = 60s en PostgreSQL, 75 s sin tráfico y 12 peticiones seguidas"; echo
  echo "| Configuración | códigos HTTP | ms de cada petición | avisos 'Failed to validate connection' |"; echo "|---|---|---|---|"; } >> "$R"
ocioso "max-lifetime 30 min, keepalive por defecto (2 min)" --spring.datasource.hikari.max-lifetime=1800000
ocioso "keepalive-time 30 s" --spring.datasource.hikari.max-lifetime=1800000 --spring.datasource.hikari.keepalive-time=30000
psqlv -q -c "alter system reset idle_session_timeout" -c "select pg_reload_conf()" > /dev/null
a1=$(sed -n 1p "$OUT/ocioso.tsv" | awk '{print $NF}'); a2=$(sed -n 2p "$OUT/ocioso.tsv" | awk '{print $NF}')
criterio "paso 8: con keepalive de 2 min el pool encuentra conexiones cortadas ($a1 avisos); con 30 s, ninguna ($a2)" '[ "$a1" -gt 0 ] && [ "$a2" -eq 0 ]'
fi

echo "== Paso 9: minimum-idle 1 tras un rato sin tráfico"
pico() {   # pico etiqueta [args]
  local etiqueta=$1; shift
  JVM="-Dcom.zaxxer.hikari.housekeeping.periodMs=2000" arrancar "pico-$etiqueta" 46251 "$@"
  calentar 46251
  K6_URL=http://localhost:46251/consulta VUS=50 DURACION=3s k6 run -q --summary-export "$OUT/pico-calor-$etiqueta.json" "$RAIZ_VER/herramientas/carga.js" > /dev/null 2>&1 || true
  sleep 25
  local total
  total=$(metrica 46251 hikaricp.connections)
  carga "pico-$etiqueta" http://localhost:46251/consulta 50 3s
  read -r rps p95 err n < <(resumen "$OUT/pico-$etiqueta.json")
  echo "| $etiqueta | $total | $p95 | $(jq -r '.metrics.http_req_duration.max|floor' "$OUT/pico-$etiqueta.json") |" >> "$R"
  echo "$etiqueta $total $p95" >> "$OUT/pico.tsv"
  parar_todo
}
: > "$OUT/pico.tsv"
{ echo; echo "## Paso 9: pico de 50 usuarios durante 3 s tras 25 s sin tráfico"; echo
  echo "El enunciado pide cinco minutos; aquí el vigilante del pool corre cada 2 s (com.zaxxer.hikari.housekeeping.periodMs) y idle-timeout es 10 s, el mínimo que acepta HikariCP."; echo
  echo "| Pool | conexiones abiertas antes del pico | p95 del pico (ms) | máximo (ms) |"; echo "|---|---|---|---|"; } >> "$R"
pico "minimum-idle 1" --spring.datasource.hikari.minimum-idle=1 --spring.datasource.hikari.idle-timeout=10000
pico "tamaño fijo (10)"
t1=$(sed -n 1p "$OUT/pico.tsv" | awk '{print $(NF-1)}')
criterio "paso 9: con minimum-idle 1 el pool se vació hasta 1 conexión antes del pico ($t1)" '[ "${t1%.*}" = "1" ]'

echo "== Paso 10: la configuración final"
arrancar final 46261 --spring.config.additional-location="file:$AQUI/config-final.yaml"
calentar 46261
carga final http://localhost:46261/consulta 50 "$DURACION"
read -r rps p95 err n < <(resumen "$OUT/final.json")
{ echo; echo "## Paso 10: config-final.yaml, una instancia, 50 usuarios"; echo; echo "$rps peticiones/s, p95 $p95 ms, errores $err %."; } >> "$R"
criterio "paso 10: la configuración final carga y atiende sin errores" '[ "$err" = "0" ] && [ "$(metrica 46261 hikaricp.connections.max | cut -d. -f1)" = "6" ]'
parar_todo

echo; cat "$R" | grep -E "^- \["
[ "$fallos" -eq 0 ] || { echo "taller 02: $fallos criterios fallaron"; exit 1; }
echo "taller 02: todo correcto"
