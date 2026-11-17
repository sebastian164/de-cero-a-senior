#!/usr/bin/env bash
# Taller 05 (integrador): la cuenta y la carga (parte 1), el panel y la disponibilidad (parte 2), el simulacro de siete situaciones
# (parte 3) y PgBouncer con seis instancias (parte 4). Escribe resultados/resultados.md.
# Dura entre 15 y 50 minutos según la máquina. Con RAPIDO=1 provoca solo tres situaciones (fuga, retención y conexiones cortadas) y salta la repetición del paso 7: unos 8 a 10 minutos.
set -euo pipefail
source "$(dirname "$0")/herramientas/lib.sh"
AQUI="$RAIZ_VER"
OUT="$AQUI/resultados"; rm -rf "$OUT"; mkdir -p "$OUT/series"
JAR="$AQUI/target/taller-05-1.0.0.jar"
R="$OUT/resultados.md"
export DB_PASSWORD='Tienda2026!'
fallos=0
SITUACIONES="fuga retencion consulta-lenta capacidad caida cortadas instancias"
[ -z "${RAPIDO:-}" ] || SITUACIONES="fuga retencion cortadas"
criterio() { if eval "$2"; then echo "   ok    $1"; echo "- [x] $1" >> "$R"; else echo "   FALLA $1"; echo "- [ ] **FALLA** $1" >> "$R"; fallos=$((fallos+1)); fi; }

(cd "$AQUI" && mvn -q -B package -DskipTests)
dc up -d --wait db > /dev/null   # la base del entorno, con max_connections = 30
preparar_tienda
# la tabla grande de la consulta lenta (la misma que usa el taller 04)
if [ "$(psqlv -tAc "select count(*) from pg_tables where tablename = 'historial'")" = 0 ]; then
  psqlv -q -c "create table historial (id bigint generated always as identity primary key, cliente_id int not null, importe int not null, relleno text not null)" \
    -c "insert into historial (cliente_id, importe, relleno) select (random() * 100000)::int, (random() * 1000)::int, md5(n::text) || md5((n + 1)::text) from generate_series(1, 6000000) n" \
    -c "grant select on historial to tienda" -c "analyze historial"
fi
psqlv -q -c "create index if not exists historial_por_cliente on historial (cliente_id)"
java "$RAIZ_VER/herramientas/Externo.java" 46799 50 > "$OUT/externo.log" 2>&1 &
EXTERNO=$!
PID=""
trap '[ -n "$PID" ] && kill "$PID" 2> /dev/null || true; kill $EXTERNO 2> /dev/null || true; docker rm -f ${PROYECTO}-pgbouncer ${PROYECTO}-pgbouncer0 ${PROYECTO}-prometheus ${PROYECTO}-grafana > /dev/null 2>&1 || true; jobs -p | xargs kill 2> /dev/null || true' EXIT
for _ in $(seq 1 40); do curl -s localhost:46799/cuenta > /dev/null && break; sleep 0.5; done
echo "# Resultados del taller 05 ($(date '+%Y-%m-%d %H:%M'))" > "$R"

echo "== Parte 1: la cuenta y la prueba de carga"
max_conn=$(psqlv -tAc "show max_connections"); reservadas=$(psqlv -tAc "show superuser_reserved_connections")
pool=$(grep -E '^\s+maximum-pool-size:' "$AQUI/src/main/resources/application.yaml" | awk '{print $2}')
presupuesto=$((max_conn - reservadas))
{ echo; echo "## Parte 1: la cuenta"; echo
  echo "- max_connections = $max_conn, superuser_reserved_connections = $reservadas -> $presupuesto para aplicaciones."
  echo "- Pool por instancia (application.yaml): $pool. Instancias normales: 3. Con despliegue gradual: 4. Con autoescalado hasta 4: 4 x $pool = $((4 * pool))."
  echo "- Con 5 instancias serían $((5 * pool)), más que $presupuesto: el autoescalado se topa en 4."; } >> "$R"
criterio "parte 1: 4 instancias x $pool = $((4 * pool)) caben en las $presupuesto conexiones disponibles, con 3 de reserva para administración" '[ $((4 * pool)) -le $((presupuesto - 3)) ]'
criterio "parte 1: la cuenta cierra justo: 5 instancias ya no caben ($((5 * pool)) > $((presupuesto - 3)))" '[ $((5 * pool)) -gt $((presupuesto - 3)) ]'
SERIE="$OUT/series/carga"
java -jar "$JAR" --server.port=46810 --spring.datasource.url="$DB_URL" > "$OUT/carga-unica.log" 2>&1 &
PID=$!
for _ in $(seq 1 120); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:46810/actuator/health)" != "000" ] && break; sleep 0.5; done
for _ in $(seq 1 30); do curl -s -o /dev/null localhost:46810/lectura/1; done
( : > "$OUT/carga-activas.tsv"; while true; do metrica 46810 hikaricp.connections.active | cut -d. -f1 >> "$OUT/carga-activas.tsv"; sleep 0.5; done ) &
MUESTREO=$!
BASES=http://localhost:46810 RATE=300 DURACION=20s HIST=5 k6 run -q --summary-export "$OUT/carga-unica.json" "$RAIZ_VER/herramientas/carga05.js" > "$OUT/carga-unica.k6.txt" 2>&1 || true
kill $MUESTREO 2> /dev/null || true; wait $MUESTREO 2> /dev/null || true
activas_max=$(sort -n "$OUT/carga-activas.tsv" | tail -1); timeouts=$(metrica 46810 hikaricp.connections.timeout | cut -d. -f1)
uso=$(curl -s localhost:46810/actuator/metrics/hikaricp.connections.usage | jq -r '[.measurements[]|{(.statistic):.value}]|add|"\((.TOTAL_TIME/.COUNT*1000)|round)"')
fallos_k6=$(jq -r '(.metrics.http_req_failed.value // 0)*100|round' "$OUT/carga-unica.json"); rps=$(jq -r '.metrics.http_reqs.rate|floor' "$OUT/carga-unica.json")
{ echo; echo "Prueba de carga de UNA instancia con el pool de $pool, 300 peticiones/s (el triple de lo que le toca con tres instancias a 300 en total), 20 s:"
  echo "$rps peticiones/s, errores $fallos_k6 %, activas máx $activas_max, timeouts $timeouts, retención media $uso ms."; } >> "$R"
criterio "parte 1 (paso 2): una instancia con pool de $pool atiende 300/s sin errores ni timeouts (activas máx $activas_max)" '[ "$fallos_k6" = 0 ] && [ "$timeouts" = 0 ] && [ "$activas_max" -le "$pool" ]'
kill "$PID" 2> /dev/null || true; wait "$PID" 2> /dev/null || true; PID=""

echo "== Parte 2: panel, alertas, disponibilidad (se comprueba con las series del simulacro) y simulacro"
"$AQUI/simulacro.sh" $SITUACIONES

echo "== Parte 2: alertas y panel contra Prometheus y Grafana reales (prometheus.sh)"
"$AQUI/prometheus.sh" || { echo "   FALLA prometheus.sh"; echo "- [ ] **FALLA** prometheus.sh (ver criterios arriba)" >> "$R"; fallos=$((fallos+1)); }

esperado() {   # bash 3.2 de macOS no tiene arreglos asociativos
  case "$1" in caida) echo caida-de-la-base ;; cortadas) echo conexiones-cortadas ;; instancias) echo demasiadas-instancias ;; *) echo "$1" ;; esac
}
diagnosticar() {   # diagnosticar nivel situacion -> "etiqueta segundos"
  python3 -I "$RAIZ_VER/herramientas/diagnostico.py" "$OUT/series/$2" "$1" | tee -a "$OUT/diagnosticos-nivel$1.jsonl" | jq -r '"\(.diagnostico.etiqueta // "sin-diagnostico") \(.diagnostico.segundos // 0)"'
}
{ echo; echo "## Parte 3: el simulacro, con carga continua de 200 peticiones/s sobre tres instancias (pool de 6 cada una)"; echo
  echo "Quien diagnostica (herramientas/diagnostico.py) solo lee lo grabado: el panel del curso y los registros (nivel 1) y, en el segundo intento, también la duración por extremo y la CPU de la base (nivel 2)."; echo
  echo "| Situación | Esperado | Nivel 1: diagnóstico (s) | Nivel 2: diagnóstico (s) | Alerta temprana (s) | Alerta grave (s) |"; echo "|---|---|---|---|---|---|"; } >> "$R"
for s in $SITUACIONES; do
  read -r e1 t1 < <(diagnosticar 1 "$s"); read -r e2 t2 < <(diagnosticar 2 "$s")
  temprana=$(tail -1 "$OUT/diagnosticos-nivel2.jsonl" | jq -r '.alerta_temprana_s // "no suena"'); grave=$(tail -1 "$OUT/diagnosticos-nivel2.jsonl" | jq -r '.alerta_grave_s // "no suena"')
  echo "| $s | $(esperado "$s") | $e1 ($t1) | $e2 ($t2) | $temprana | $grave |" >> "$R"
  echo "   $s: nivel 1 -> $e1 en $t1 s; nivel 2 -> $e2 en $t2 s; alerta temprana $temprana, grave $grave"
  echo "$s $e1 $t1 $e2 $t2" >> "$OUT/diagnosticos.tsv"
done
dato() { awk -v s="$1" -v c="$2" '$1==s {print $c}' "$OUT/diagnosticos.tsv"; }
# Lo que el diagnóstico de la fuga debe decir (en qué línea) y lo que vio el usuario al cortar las sesiones
linea_fuga=$(grep -h -A12 "Connection leak detection triggered" "$OUT/series/fuga".*.log | grep -m1 -E "^[[:space:]]+at taller\." | sed 's/^[[:space:]]*//' || true)
vistas=$(jq -s '[.[].metrics.http_req_failed.passes // 0] | add' "$OUT/series/cortadas".k6-*.json)
total_cortadas=$(jq -s '[.[].metrics.http_reqs.count // 0] | add' "$OUT/series/cortadas".k6-*.json)
{ echo; echo "### Dos detalles de la tabla"; echo
  echo "- Fuga, en qué línea: el aviso de \`leak-detection\` trae la traza de quien tomó la conexión; la primera línea de la aplicación es \`$linea_fuga\`."
  echo "- Conexiones cortadas desde fuera: el usuario vio $vistas peticiones con error de $total_cortadas (200 por segundo durante 60 s); el pool repuso las conexiones solo."; } >> "$R"
criterio "fuga: el registro dice en qué línea se tomó la conexión que no se devolvió ($linea_fuga)" 'grep -q "Extremos.fuga" <<< "$linea_fuga"'
criterio "conexiones cortadas: se registró lo que vio el usuario ($vistas errores de $total_cortadas peticiones)" '[ -n "$vistas" ] && [ "$vistas" != null ] && [ "$total_cortadas" -gt 0 ]'

for s in $SITUACIONES; do
  criterio "simulacro: $s se diagnostica como $(esperado "$s") con el panel y el registro del segundo intento (nivel 2: $(dato $s 4) en $(dato $s 5) s)" '[ "$(dato $s 4)" = "$(esperado "$s")" ]'
done
if [[ " $SITUACIONES " == *" caida "* ]]; then
criterio "parte 2 (paso 5): durante la caída de la base la disponibilidad falla (503) y la vida sigue respondiendo 200" \
  'jq -e -s "[.[] | .instancias[] | select(.readiness == 503)] | length > 0" "$OUT/series/caida.jsonl" > /dev/null && jq -e -s "[.[] | .instancias[] | select(.max != null) | .liveness] | all(. == 200)" "$OUT/series/caida.jsonl" > /dev/null'
t0c=$(cat "$OUT/series/caida.t0"); arr=$(cat "$OUT/series/caida.arranque")
recuperacion=$(jq -s --argjson a "$arr" '[.[] | select(.t > $a and ([.instancias[] | select(.max != null) | .readiness] | all(. == 200)))][0].t - $a | floor' "$OUT/series/caida.jsonl")
parada=$(cat "$OUT/series/caida.parada")
{ echo; echo "Caída de la base: parada a los $(awk -v a="$parada" -v b="$t0c" 'BEGIN{printf "%.0f", a-b}') s de la provocación, arrancada de nuevo 30 s después. La disponibilidad volvió a 200 en todas las instancias $recuperacion s después de arrancar la base, sin reiniciar ninguna aplicación."; } >> "$R"
criterio "parte 3: tras la caída la aplicación se recupera sola (disponibilidad en 200 a los $recuperacion s de arrancar la base)" '[ -n "$recuperacion" ] && [ "$recuperacion" != null ] && [ "$recuperacion" -le 40 ]'
criterio "parte 3: ninguna instancia tuvo que reiniciarse en la caída (los tres procesos de la serie siguen respondiendo)" '[ "$(jq -s "[.[] | .instancias | to_entries[] | select(.key | test(\"4680[123]\")) | .value.max] | map(select(. == null)) | length" "$OUT/series/caida.jsonl")" -le 30 ]'
fi

if [ -z "${RAPIDO:-}" ]; then
echo "== Paso 7: repetir las dos situaciones que más costaron, con lo que faltó ya en el panel"
dos=$(awk '{print $3+0 " " $1}' "$OUT/diagnosticos.tsv" | sort -rn | head -2 | awk '{print $2}' | tr '\n' ' ')
{ echo; echo "## Paso 7: las dos que más costaron en el nivel 1 se repiten (nuevo aprovisionamiento y nueva provocación): $dos"; echo
  echo "| Situación | Nivel 1 (primera vez) | Nivel 2 (repetición) |"; echo "|---|---|---|"; } >> "$R"
mkdir -p "$OUT/series-repeticion"
OUT_ORIG="$OUT"
for s in $dos; do
  mv "$OUT/series/$s".* "$OUT/series-repeticion/" 2> /dev/null || true
done
"$AQUI/simulacro.sh" $dos
for s in $dos; do
  read -r e2 t2 < <(diagnosticar 2 "$s")
  echo "| $s | $(dato $s 2) en $(dato $s 3) s | $e2 en $t2 s |" >> "$R"
  criterio "paso 7: repetida, $s se diagnostica como $(esperado "$s") ($e2 en $t2 s)" '[ "$e2" = "$(esperado "$s")" ]'
done
fi

echo "== Parte 4: PgBouncer delante de la base y seis instancias"
docker rm -f ${PROYECTO}-pgbouncer > /dev/null 2>&1 || true
docker run -d --name ${PROYECTO}-pgbouncer --network ${PROYECTO}_default -p 46433:5432 \
  -e DB_HOST=db -e DB_USER=tienda -e DB_PASSWORD="$DB_PASSWORD" -e DB_NAME=tienda -e POOL_MODE=transaction -e AUTH_TYPE=scram-sha-256 \
  -e DEFAULT_POOL_SIZE=15 -e MAX_CLIENT_CONN=200 edoburu/pgbouncer:v1.26.1-p0 > /dev/null
for _ in $(seq 1 30); do PGPASSWORD="$DB_PASSWORD" psql_ok=$(docker exec -e PGPASSWORD="$DB_PASSWORD" ${PROYECTO}-pgbouncer psql -h localhost -U tienda -d tienda -tAc "select 1" 2> /dev/null || true); [ "$psql_ok" = 1 ] && break; sleep 1; done
PGB_URL="jdbc:postgresql://localhost:46433/tienda"
for p in 46821 46822 46823 46824 46825 46826; do
  java -jar "$JAR" --server.port=$p --spring.datasource.url="$PGB_URL" > "$OUT/pgbouncer-$p.log" 2>&1 &
done
for p in 46821 46822 46823 46824 46825 46826; do for _ in $(seq 1 120); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:$p/actuator/health)" != "000" ] && break; sleep 0.5; done; done
BASES=$(for p in 46821 46822 46823 46824 46825 46826; do printf 'http://localhost:%s,' $p; done | sed 's/,$//')
BASES="$BASES" RATE=200 DURACION=20s HIST=0 k6 run -q --summary-export "$OUT/pgbouncer.json" "$RAIZ_VER/herramientas/carga05.js" > "$OUT/pgbouncer.k6.txt" 2>&1 &
K6=$!; sleep 12
clientes=$(docker exec ${PROYECTO}-pgbouncer sh -c 'psql -h localhost -U tienda -d pgbouncer -tAc "show pools" 2>/dev/null' || true)
en_la_base=$(psqlv -tAc "select count(*) from pg_stat_activity where usename = 'tienda'")
total_clientes=$(for p in 46821 46822 46823 46824 46825 46826; do metrica $p hikaricp.connections | cut -d. -f1; done | paste -sd+ - | bc)
wait $K6
err_pgb=$(jq -r '(.metrics.http_req_failed.value // 0)*100|round' "$OUT/pgbouncer.json"); rps_pgb=$(jq -r '.metrics.http_reqs.rate|floor' "$OUT/pgbouncer.json")
# la característica de sesión que cambia: un bloqueo de sesión (advisory lock) que un cliente toma y no suelta en modo transacción
docker exec -e PGPASSWORD="$DB_PASSWORD" ${PROYECTO}-pgbouncer psql -h localhost -U tienda -d tienda -tAc "select pg_advisory_lock(4242)" > /dev/null
bloqueos_tras_soltar=$(psqlv -tAc "select count(*) from pg_locks where locktype = 'advisory'")
{ echo; echo "## Parte 4: PgBouncer ($(docker run --rm --entrypoint pgbouncer edoburu/pgbouncer:v1.26.1-p0 --version | head -1)), modo transacción, default_pool_size 15, seis instancias de pool 6"; echo
  echo "- Conexiones de las aplicaciones hacia PgBouncer: $total_clientes (6 x 6 = 36, más que las $presupuesto que admitiría la base directamente)."
  echo "- Conexiones de PgBouncer hacia PostgreSQL con usuario tienda, a mitad de la carga: $en_la_base."
  echo "- Carga de 200 peticiones/s durante 20 s: $rps_pgb peticiones/s, errores $err_pgb %."
  echo "- Característica de sesión: un \`pg_advisory_lock(4242)\` tomado por un cliente en modo transacción deja \`$bloqueos_tras_soltar\` bloqueo(s) de sesión retenido(s) en PostgreSQL cuando el cliente se va: el bloqueo pertenece a la conexión del servidor, no al cliente."; } >> "$R"
criterio "parte 4: seis instancias de pool 6 (36 conexiones de cliente) funcionan detrás de PgBouncer sin errores ($err_pgb %) con solo $en_la_base conexiones reales" '[ "$err_pgb" = 0 ] && [ "$total_clientes" -gt "$presupuesto" ] && [ "$en_la_base" -le 15 ]'
criterio "parte 4: el bloqueo de sesión se queda en la conexión del servidor en modo transacción ($bloqueos_tras_soltar)" '[ "$bloqueos_tras_soltar" -ge 1 ]'
# prepared statements de servidor (prepareThreshold=1) con ocho clientes concurrentes: con el valor por defecto de PgBouncer 1.26.1
# (max_prepared_statements = 200) funcionan; con max_prepared_statements = 0 fallan ("prepared statement ... already exists")
mkdir -p "$OUT/pgbouncer"; unzip -o -q -j "$JAR" 'BOOT-INF/lib/postgresql-*.jar' -d "$OUT/pgbouncer"
DRIVER=$(ls "$OUT/pgbouncer"/postgresql-*.jar | head -1)
con_defecto=$(java -cp "$DRIVER" "$RAIZ_VER/herramientas/Preparadas.java" "$PGB_URL" tienda "$DB_PASSWORD")
docker cp ${PROYECTO}-pgbouncer:/etc/pgbouncer/pgbouncer.ini "$OUT/pgbouncer/pgbouncer.ini"
docker cp ${PROYECTO}-pgbouncer:/etc/pgbouncer/userlist.txt "$OUT/pgbouncer/userlist.txt"
sed -i.bak 's/^pool_mode = transaction/pool_mode = transaction\nmax_prepared_statements = 0/' "$OUT/pgbouncer/pgbouncer.ini"
docker rm -f ${PROYECTO}-pgbouncer0 > /dev/null 2>&1 || true
docker run -d --name ${PROYECTO}-pgbouncer0 --network ${PROYECTO}_default -p 46434:5432 -v "$OUT/pgbouncer/pgbouncer.ini:/etc/pgbouncer/pgbouncer.ini:ro" \
  -v "$OUT/pgbouncer/userlist.txt:/etc/pgbouncer/userlist.txt:ro" -e DB_HOST=db -e DB_USER=tienda -e DB_PASSWORD="$DB_PASSWORD" -e DB_NAME=tienda edoburu/pgbouncer:v1.26.1-p0 > /dev/null
for _ in $(seq 1 30); do [ "$(docker exec -e PGPASSWORD="$DB_PASSWORD" ${PROYECTO}-pgbouncer0 psql -h localhost -U tienda -d tienda -tAc 'select 1' 2> /dev/null || true)" = 1 ] && break; sleep 1; done
sin_preparadas=$(java -cp "$DRIVER" "$RAIZ_VER/herramientas/Preparadas.java" jdbc:postgresql://localhost:46434/tienda tienda "$DB_PASSWORD")
docker rm -f ${PROYECTO}-pgbouncer0 > /dev/null
{ echo "- Prepared statements de servidor (\`prepareThreshold=1\`, ocho clientes): con el valor por defecto de esta versión (\`max_prepared_statements\` = 200): \`$con_defecto\`; con \`max_prepared_statements = 0\`: \`$sin_preparadas\`."; } >> "$R"
criterio "parte 4: los prepared statements de servidor funcionan con max_prepared_statements por defecto ($con_defecto) y fallan con 0 ($sin_preparadas)" \
  '[[ "$con_defecto" == ok* ]] && [[ "$sin_preparadas" == *"prepared statement"* ]]'
psqlv -q -c "select pg_advisory_unlock_all()" > /dev/null 2>&1 || true
jobs -p | xargs kill 2> /dev/null || true
docker rm -f ${PROYECTO}-pgbouncer > /dev/null

echo; grep -E "^- \[" "$R"
[ "$fallos" -eq 0 ] || { echo "taller 05: $fallos criterios fallaron"; exit 1; }
echo "taller 05: todo correcto"
