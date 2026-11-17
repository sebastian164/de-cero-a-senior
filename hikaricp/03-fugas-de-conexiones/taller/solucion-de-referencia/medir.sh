#!/usr/bin/env bash
# Taller 03: ejecuta los pasos 2 a 11 contra la base del entorno y escribe resultados/resultados.md.
# Termina con error si no se cumple un criterio. Requiere la base levantada (la levanta el propio script).
set -euo pipefail
source "$(dirname "$0")/herramientas/lib.sh"
source "$RAIZ_VER/herramientas/resumen.sh"
export DB_PASSWORD='Tienda2026!'
AQUI="$RAIZ_VER"
OUT="$AQUI/resultados"; rm -rf "$OUT"; mkdir -p "$OUT"
JAR="$AQUI/target/taller-03-1.0.0.jar"
FUENTE="$AQUI/src/main/java/taller/Clientes.java"
R="$OUT/resultados.md"
fallos=0
criterio() { if eval "$2"; then echo "   ok    $1"; echo "- [x] $1" >> "$R"; else echo "   FALLA $1"; echo "- [ ] **FALLA** $1" >> "$R"; fallos=$((fallos+1)); fi; }

(cd "$AQUI" && mvn -q -B package -DskipTests)
dc up -d --wait db > /dev/null   # la base del entorno, con max_connections = 30
preparar_tienda

PID=""; MUESTREO=""
arrancar() {   # arrancar nombre puerto [args...]
  local nombre=$1 puerto=$2; shift 2
  java -jar "$JAR" --server.port="$puerto" --spring.datasource.url="$DB_URL" "$@" > "$OUT/$nombre.log" 2>&1 &
  PID=$!
  esperar_http "$puerto" || { echo "no arrancó $nombre"; tail -40 "$OUT/$nombre.log"; exit 1; }
}
parar() { [ -n "$MUESTREO" ] && { kill "$MUESTREO" 2> /dev/null || true; wait "$MUESTREO" 2> /dev/null || true; MUESTREO=""; }
          [ -n "$PID" ] && { kill "$PID" 2> /dev/null || true; wait "$PID" 2> /dev/null || true; PID=""; }; return 0; }
trap parar EXIT

# corrida nombre puerto ruta duracion [args...]: tráfico con una de cada 50 por el camino raro, 10 s de reposo al final
corrida() {
  local nombre=$1 puerto=$2 ruta=$3 duracion=$4; shift 4
  arrancar "$nombre" "$puerto" "$@"
  muestrear "$puerto" hikaricp.connections.active "$OUT/$nombre.tsv"
  BASE="http://localhost:$puerto$ruta" RATE=25 DURACION="$duracion" k6 run --summary-export "$OUT/$nombre.json" \
    "$RAIZ_VER/herramientas/fuga.js" > "$OUT/$nombre.k6.txt" 2>&1 || true
  sleep 10
}
primer_fallo() { grep -o 'PRIMER_FALLO peticion=[0-9]*' "$OUT/$1.k6.txt" | cut -d= -f2 | sort -n | head -1; }
ultima_activa() { tail -1 "$OUT/$1.tsv" | awk '{print $2}'; }

echo "# Resultados del taller 03 ($(date '+%Y-%m-%d %H:%M'))" > "$R"

echo "== Pasos 2 y 3: pool de 5, 25 peticiones/s, una de cada 50 con id negativo"
corrida fuga-5 46301 /fuga/clientes 30s
estados5=$(psqlv -tAc "select state || ': ' || count(*) from pg_stat_activity where usename = 'tienda' group by state" | tr '\n' ' ')
psqlv -c "select pid, state, now() - state_change as en_ese_estado_desde, left(query, 50) as ultima_consulta from pg_stat_activity where usename = 'tienda'" > "$OUT/paso6-pg_stat_activity.txt"
parar
p5=$(primer_fallo fuga-5)
mensaje=$(grep -m1 -o "clientes - Connection is not available, request timed out after [0-9]*ms ([^)]*)" "$OUT/fuga-5.log" || true)
read -r rps p95 err n < <(resumen "$OUT/fuga-5.json")
{ echo; echo "## Pasos 2 y 3: pool de 5"; echo
  echo "$n peticiones, $err % con error. La primera petición que falla es la número **$p5**: las 5 conexiones se fugaron en las peticiones 50, 100, 150, 200 y 250."
  echo "Ninguna de las que fallan pasa por el camino defectuoso: son ids positivos, inocentes."; echo
  echo "Mensaje completo (registro de la aplicación):"; echo; echo '```'
  grep -m1 -B0 "Connection is not available" "$OUT/fuga-5.log" | cut -c1-600
  echo '```'; echo
  echo "Conexiones activas, muestreadas cada segundo con /actuator/metrics/hikaricp.connections.active: [grafica-activas.svg](grafica-activas.svg)"; } >> "$R"
criterio "paso 3: la primera petición que falla llega después de la 250ª ($p5)" '[ -n "$p5" ] && [ "$p5" -gt 250 ] && [ "$p5" -lt 400 ]'
criterio "paso 3: el mensaje es el de HikariCP con el estado del pool: $mensaje" \
  'grep -Eq "^clientes - Connection is not available, request timed out after [0-9]+ms \(total=5, active=5, idle=0, waiting=[0-9]+\)$" <<< "$mensaje"'
criterio "paso 2: las activas suben en escalera hasta 5 y no bajan al parar el tráfico ($(ultima_activa fuga-5))" '[ "$(ultima_activa fuga-5)" = "5" ]'

echo "== Paso 4: pool de 20"
corrida fuga-20 46302 /fuga/clientes 60s --spring.datasource.hikari.maximum-pool-size=20
parar
p20=$(primer_fallo fuga-20)
read -r rps p95 err20 n20 < <(resumen "$OUT/fuga-20.json")
{ echo; echo "## Paso 4: pool de 20"; echo
  echo "$n20 peticiones en 60 s, $err20 % con error. Primera petición que falla: **$p20** (con 5 fue la $p5). El fallo llega cuatro veces más tarde y es el mismo."; } >> "$R"
criterio "paso 4: con 20 conexiones el fallo llega después de la 1000ª ($p20), pero llega" '[ -n "$p20" ] && [ "$p20" -gt 1000 ]'

echo "== Paso 5: detección de fugas a 2 s"
linea=$(grep -n 'Connection conexion = dataSource.getConnection();' "$FUENTE" | head -1 | cut -d: -f1)
corrida fuga-deteccion 46303 /fuga/clientes 12s --spring.datasource.hikari.leak-detection-threshold=2000
parar
traza=$(grep -A12 -m1 "Connection leak detection triggered" "$OUT/fuga-deteccion.log" | grep -m1 "at taller\." | sed 's/^[[:space:]]*//; s/ ~\[.*$//')
{ echo; echo "## Paso 5: leak-detection-threshold = 2000"; echo; echo '```'
  grep -A10 -m1 "Connection leak detection triggered" "$OUT/fuga-deteccion.log" | cut -c1-300
  echo '```'; echo
  echo "La primera línea de nuestro código en la traza es \`$traza\`: la línea $linea de Clientes.java, el \`getConnection()\` de \`conFuga\`."; } >> "$R"
criterio "paso 5: la traza del aviso señala Clientes.java:$linea, donde conFuga toma la conexión" '[ "$traza" = "at taller.Clientes.conFuga(Clientes.java:$linea)" ]'

{ echo; echo "## Paso 6: pg_stat_activity con las 5 conexiones fugadas"; echo; echo "Estados: $estados5"; echo; echo '```'; cat "$OUT/paso6-pg_stat_activity.txt"; echo '```'; } >> "$R"
criterio "paso 6: las conexiones fugadas están en estado idle (con autocommit no hay transacción abierta): $estados5" '[[ "$estados5" == "idle: 5 " ]]'

echo "== Paso 7: corregido con try-with-resources"
corrida corregido 46304 /corregido/clientes 30s
parar
read -r rps p95 errc nc < <(resumen "$OUT/corregido.json")
python3 -I "$RAIZ_VER/herramientas/serie.py" "$OUT/grafica-activas.svg" "conexiones activas" \
  "con fuga, pool 5=$OUT/fuga-5.tsv" "con fuga, pool 20=$OUT/fuga-20.tsv" "corregido, pool 5=$OUT/corregido.tsv"
{ echo; echo "## Paso 7: try-with-resources"; echo; echo "$nc peticiones, $errc % con error. Activas al final del reposo: $(ultima_activa corregido)."; } >> "$R"
criterio "paso 7: sin errores y las activas vuelven a 0 al parar el tráfico" '[ "$errc" = "0" ] && [ "$(ultima_activa corregido)" = "0" ]'

echo "== Pasos 8 y 9: transacción a medias y falsa alarma"
arrancar transaccion 46305 --spring.datasource.hikari.leak-detection-threshold=2000
psqlv -q -c "update cliente set visitas = 0 where id in (3, 4)"
r3=$(curl -s localhost:46305/transaccion/clientes/3); r4=$(curl -s localhost:46305/transaccion/clientes/4)
visitas=$(psqlv -tAc "select string_agg(id || '=' || visitas, ' ' order by id) from cliente where id in (3, 4)")
otra=$(psqlv -tAc "set lock_timeout = '1s'; update cliente set visitas = visitas where id = 3" 2>&1)
activas8=$(metrica 46305 hikaricp.connections.active | cut -d. -f1)
curl -s localhost:46305/informe > /dev/null
sleep 1
parar
{ echo; echo "## Paso 8: setAutoCommit(false) y un camino sin commit ni rollback, pero con close()"; echo
  echo "- Respuestas: id 3 -> \"$r3\"; id 4 -> \"$r4\". Visitas después: $visitas. Conexiones activas después: $activas8."
  echo "- Otra sesión actualiza la fila 3 con lock_timeout de 1 s: \`$otra\` (no quedó bloqueo)."
  echo "- Registro de HikariCP (ProxyConnection en DEBUG):"; echo; echo '```'
  grep -m1 "Executed rollback on connection" "$OUT/transaccion.log" | sed -E 's/.*(clientes - Executed)/\1/'
  echo '```'
  echo; echo "## Paso 9: una operación legítima de 3 s con el umbral a 2 s"; echo; echo '```'
  grep -m1 "Connection leak detection triggered" "$OUT/transaccion.log" | sed -E 's/.*(Connection leak)/\1/' | cut -c1-200
  grep -m1 "Previously reported leaked connection" "$OUT/transaccion.log" | sed -E 's/.*(Previously)/\1/'
  echo '```'; } >> "$R"
criterio "paso 8: la conexión no se fuga y HikariCP revierte al devolverla: 3=0 (revertida), 4=1 (confirmada)" \
  '[ "$visitas" = "3=0 4=1" ] && [ "$activas8" = "0" ] && grep -q "clientes - Executed rollback on connection .* due to dirty commit state on close()" "$OUT/transaccion.log"'
criterio "paso 8: el bloqueo de la fila se liberó con la reversión" '[ "$otra" = "UPDATE 1" ] || [[ "$otra" == *"UPDATE 1"* ]]'
criterio "paso 9: el aviso salta y luego HikariCP dice que la conexión volvió: falsa alarma" \
  'grep -q "Connection leak detection triggered" "$OUT/transaccion.log" && grep -q "Previously reported leaked connection .* on thread .* was returned to the pool (unleaked)" "$OUT/transaccion.log"'

echo "== Paso 10: JdbcTemplate"
corrida plantilla 46306 /plantilla/clientes 15s
parar
read -r rps p95 errp np < <(resumen "$OUT/plantilla.json")
{ echo; echo "## Paso 10: JdbcTemplate"; echo; echo "$np peticiones, $errp % con error, activas al final: $(ultima_activa plantilla)."; } >> "$R"
criterio "paso 10: con JdbcTemplate, sin errores y sin conexiones fuera" '[ "$errp" = "0" ] && [ "$(ultima_activa plantilla)" = "0" ]'

echo "== Paso 11: la prueba automática"
if (cd "$AQUI" && mvn -q -B test -Ddb.puerto="$DB_PUERTO" > "$OUT/mvn-test.txt" 2>&1); then pruebas=ok; else pruebas=falla; fi
{ echo; echo "## Paso 11: pruebas"; echo; echo '```'; grep -h "Tests run" "$AQUI"/target/surefire-reports/*.txt; echo '```'; } >> "$R"
criterio "paso 11: SinFugasTest pasa con los extremos corregidos y DetectaFugaTest demuestra que fallaría con la fuga" '[ "$pruebas" = ok ]'

echo; grep -E "^- \[" "$R"
[ "$fallos" -eq 0 ] || { echo "taller 03: $fallos criterios fallaron"; exit 1; }
echo "taller 03: todo correcto"
