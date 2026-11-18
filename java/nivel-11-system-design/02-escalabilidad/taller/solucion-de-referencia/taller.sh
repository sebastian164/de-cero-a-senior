#!/usr/bin/env bash
# Solución del taller 11-02 (escalabilidad): saturación, índice, caché, segunda instancia, sesión, cola, particionamiento.
# Compila la aplicación pedidos, levanta PostgreSQL y Redis con Docker Compose y lo baja todo al terminar.
# Variables opcionales: PROYECTO (n11-02), PUERTO_PG (5432), PUERTO_REDIS (6379), PUERTO_APP1 (8090), PUERTO_APP2 (8091),
# PUERTO_NGINX (8092).
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; source "$AQUI/comun.sh"
requiere docker k6 jq curl python3 java rsync
cd "$AQUI"
export PROYECTO=${PROYECTO:-n11-02}
export PUERTO_PG=${PUERTO_PG:-5432} PUERTO_REDIS=${PUERTO_REDIS:-6379}
export PUERTO_APP1=${PUERTO_APP1:-8090} PUERTO_APP2=${PUERTO_APP2:-8091} PUERTO_NGINX=${PUERTO_NGINX:-8092}
export IMAGEN=$PROYECTO/pedidos:1.4.2
TRABAJO=$(mktemp -d)
RESULTADOS=$TRABAJO/resultados.txt; : > "$RESULTADOS"
anotar() { echo "$*" | tee -a "$RESULTADOS" | sed 's/^/    /'; }
PIDS=()
parar() { for p in ${PIDS[@]+"${PIDS[@]}"}; do kill "$p" 2>/dev/null || true; done; PIDS=(); docker rm -f "$PROYECTO-nginx" >/dev/null 2>&1 || true; sleep 2; }
parar_instancias() { docker rm -f "$PROYECTO-app1" "$PROYECTO-app2" "$PROYECTO-nginx" >/dev/null 2>&1 || true; }
bajar() {
  parar; parar_instancias
  docker compose -p "$PROYECTO" -f "$AQUI/compose.yaml" down -v --remove-orphans >/dev/null 2>&1 || true
  docker image rm "$IMAGEN" >/dev/null 2>&1 || true
  rm -rf "$TRABAJO"
}
trap bajar EXIT

paso "Compilando la aplicación, construyendo su imagen y levantando PostgreSQL y Redis"
rsync -a --exclude target "$AQUI/aplicacion/" "$TRABAJO/app/"
( cd "$TRABAJO/app" && ./mvnw -q -B -DskipTests package ) || falla "no se pudo compilar la aplicación (necesitas JDK 21)"
JAR=$TRABAJO/app/target/pedidos-1.4.2.jar; test -s "$JAR"
docker build -q -t "$IMAGEN" "$AQUI/aplicacion" >/dev/null
docker compose -p "$PROYECTO" -f "$AQUI/compose.yaml" up -d --wait >/dev/null

PSQL() { docker exec -i $PROYECTO-postgres psql -U postgres -v ON_ERROR_STOP=1 -qtA "$@"; }
arrancar() { # arrancar puerto env...
  local puerto=$1; shift
  env SERVER_PORT=$puerto SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PUERTO_PG/n11_02 SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=local "$@" \
    java -jar "$JAR" > "$TRABAJO/n11-02-$puerto.log" 2>&1 & PIDS+=($!)
  esperar_http "http://localhost:$puerto/actuator/health/readiness" 90 || { tail -20 "$TRABAJO/n11-02-$puerto.log"; falla "no arrancó $puerto"; }
}
medir() { # medir etiqueta base vus → imprime "rps p95"
  local f="$TRABAJO/k6-n11-02-$1.json"
  k6 run -q --summary-export="$f" -e BASE="$2" -e VUS="$3" -e DUR=10s "$AQUI/carga.js" >/dev/null 2>&1 || true
  jq -r '"\(.metrics.http_reqs.rate|floor) \(.metrics.http_req_duration["p(95)"]|floor) \(.metrics.checks.fails)"' "$f"
}

paso "Preparación: base con 1.000.000 de pedidos y la aplicación sin índice ni caché"
docker exec $PROYECTO-postgres psql -U postgres -qc "DROP DATABASE IF EXISTS n11_02 WITH (FORCE)" -c "CREATE DATABASE n11_02" >/dev/null
arrancar $PUERTO_APP1
PSQL -d n11_02 -c "INSERT INTO pedidos(cliente_id,total) SELECT 1 + (random()*49999)::int, (random()*100)::numeric(12,2) FROM generate_series(1,1000000)"
PSQL -d n11_02 -c "ANALYZE pedidos"
comprobar "1.000.000 de filas, sin índice sobre cliente_id" bash -c "[ \"\$(docker exec $PROYECTO-postgres psql -U postgres -d n11_02 -tAc \"select count(*) from pedidos\")\" = 1000000 ] && [ \"\$(docker exec $PROYECTO-postgres psql -U postgres -d n11_02 -tAc \"select count(*) from pg_indexes where tablename='pedidos' and indexdef like '%cliente_id%'\")\" = 0 ]"

paso "1. Punto de saturación (sin índice, sin caché, una instancia)"
echo "    vus  peticiones/s  p95(ms)"
prev=0; sat=""
for v in 1 4 16 32 64; do
  read -r rps p95 fallos < <(medir base-$v http://localhost:$PUERTO_APP1 $v)
  echo "    $v  $rps  $p95"
  anotar "base_vus=$v rps=$rps p95_ms=$p95 fallos=$fallos"
  if [ -z "$sat" ] && [ "$prev" -gt 0 ] && [ "$rps" -lt $((prev * 13 / 10)) ]; then sat=$v; fi
  prev=$rps; rps_base=$rps; p95_base=$p95
  [ "$v" = 32 ] && { rps_b32=$rps; p95_b32=$p95; }
done
anotar "saturacion_cerca_de_vus=${sat:-64}"
( k6 run -q -e BASE=http://localhost:$PUERTO_APP1 -e VUS=32 -e DUR=8s "$AQUI/carga.js" >/dev/null 2>&1 & ) ; sleep 4
cpu_bd=$(docker stats --no-stream --format '{{.CPUPerc}}' $PROYECTO-postgres | tr -d '%'); cpu_app=$(ps -o %cpu= -p "${PIDS[0]}" | tr -d ' ')
sleep 6
anotar "durante_la_carga: cpu_postgres=${cpu_bd}% cpu_jvm=${cpu_app}%"
comprobar "el recurso que se agota es la CPU de PostgreSQL (recorre 1M de filas por petición): ${cpu_bd}% frente a ${cpu_app}% de la JVM" python3 -c "import sys; sys.exit(0 if float('$cpu_bd') > float('$cpu_app') else 1)"
comprobar "el plan de la consulta es un recorrido secuencial" bash -c "docker exec $PROYECTO-postgres psql -U postgres -d n11_02 -tAc 'explain select count(*) from pedidos where cliente_id=77' | grep -q 'Seq Scan'"

paso "2a. Índice"
PSQL -d n11_02 -c "CREATE INDEX pedidos_cliente_idx ON pedidos(cliente_id)" -c "ANALYZE pedidos"
read -r rps_idx p95_idx f < <(medir indice http://localhost:$PUERTO_APP1 32)
anotar "con_indice: rps=$rps_idx p95_ms=$p95_idx (antes, con los mismos 32 vus: rps=$rps_b32 p95=$p95_b32)"
comprobar "el índice multiplica el rendimiento (x$((rps_idx / (rps_b32>0?rps_b32:1))))" test "$rps_idx" -gt $((rps_b32 * 3))
comprobar "el plan usa el índice" bash -c "docker exec $PROYECTO-postgres psql -U postgres -d n11_02 -tAc 'explain select count(*) from pedidos where cliente_id=77' | grep -qi 'index'"

paso "2b. Caché (Caffeine, 60 s)"
parar; arrancar $PUERTO_APP1 CACHE_TIPO=caffeine
medir calentamiento http://localhost:$PUERTO_APP1 32 >/dev/null
read -r rps_cache p95_cache f < <(medir cache http://localhost:$PUERTO_APP1 32)
anotar "con_indice_y_cache: rps=$rps_cache p95_ms=$p95_cache"
comprobar "la caché mejora lo que dejó el índice ($rps_cache > $rps_idx peticiones/s)" test "$rps_cache" -gt "$rps_idx"

paso "2c. Escalar horizontalmente: una instancia frente a dos tras nginx (cada una limitada a 0,5 CPU)"
# Si las JVM se reparten todos los núcleos de la máquina, una segunda instancia no aporta nada (el cuello no es la
# aplicación). Con --cpus=0.5 por instancia el cuello vuelve a ser la CPU de la aplicación, que es el caso en que escalar ayuda.
RED=${PROYECTO}_default
parar; docker rm -f $PROYECTO-app1 $PROYECTO-app2 >/dev/null 2>&1 || true
instancia() { # instancia n puerto_publicado env...
  local n=$1 pub=$2; shift 2
  docker run -d --name $PROYECTO-app$n --network "$RED" --cpus=0.5 -m 1g -p 127.0.0.1:$pub:8080 \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/n11_02 -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=local \
    -e CACHE_TIPO=caffeine "$@" "$IMAGEN" >/dev/null
  esperar_http http://localhost:$pub/actuator/health/readiness 120 || { docker logs $PROYECTO-app$n | tail -20; falla "no arrancó la instancia $n"; }
}
conf_nginx() { # conf_nginx servidor...
  { echo "upstream app {"; for sv in "$@"; do echo "  server $sv:8080;"; done; echo "  keepalive 64; }"
    echo 'server { listen 80; location / { proxy_pass http://app; proxy_http_version 1.1; proxy_set_header Connection ""; add_header X-Upstream $upstream_addr always; } }'; } > "$TRABAJO/nginx-n11.conf"
}
conf_nginx $PROYECTO-app1
instancia 1 $PUERTO_APP1
nginx_arrancar() { # (re)crea nginx con la configuración actual: más fiable que recargarla en un archivo montado
  docker rm -f $PROYECTO-nginx >/dev/null 2>&1 || true
  docker run -d --name $PROYECTO-nginx --network "$RED" -p 127.0.0.1:$PUERTO_NGINX:80 -v "$TRABAJO/nginx-n11.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.27 >/dev/null
  esperar_http http://localhost:$PUERTO_NGINX/actuator/health/readiness 60 || { docker logs $PROYECTO-nginx | tail -20; falla "no arrancó nginx"; }
}
nginx_arrancar
medir calentamiento1 http://localhost:$PUERTO_NGINX 32 >/dev/null; medir calentamiento1b http://localhost:$PUERTO_NGINX 32 >/dev/null   # el JIT con 0,5 CPU tarda en calentar
mejor_de_3() { # mejor_de_3 etiqueta: la máquina es compartida; el mejor de tres intentos es el menos afectado por el ruido de otros procesos
  local mejor="0 0 0" r; for i in 1 2 3; do r=$(medir "$1-$i" http://localhost:$PUERTO_NGINX 32); [ "${r%% *}" -gt "${mejor%% *}" ] && mejor=$r; done; echo "$mejor"; }
read -r rps_1 p95_1 f1 < <(mejor_de_3 una-instancia)
anotar "una_instancia_medio_cpu_tras_nginx: rps=$rps_1 p95_ms=$p95_1 fallos=$f1"
instancia 2 $PUERTO_APP2
conf_nginx $PROYECTO-app1 $PROYECTO-app2
nginx_arrancar
medir calentamiento2 http://localhost:$PUERTO_NGINX 32 >/dev/null; medir calentamiento2b http://localhost:$PUERTO_NGINX 32 >/dev/null
read -r rps_2 p95_2 f2 < <(mejor_de_3 dos-instancias)
ganancia=$(python3 -c "print(round($rps_2 / $rps_1, 2))")
anotar "dos_instancias_medio_cpu_tras_nginx: rps=$rps_2 p95_ms=$p95_2 fallos=$f2 ganancia=x$ganancia"
comprobar "el balanceador reparte entre las dos instancias (cabecera X-Upstream)" test "$(for i in 1 2 3 4 5 6 7 8; do curl -s -D - -o /dev/null localhost:$PUERTO_NGINX/api/info | tr -d '\r' | awk -F': ' 'tolower($1)=="x-upstream"{print $2}'; done | sort -u | wc -l | tr -d ' ')" = 2
comprobar "el balanceador no pierde peticiones (fallos de k6: $f1 y $f2)" test "$f1$f2" = 00
comprobar "con la CPU de la aplicación como cuello, dos instancias rinden claramente más: al menos x1,3 (fue x$ganancia; mejor de tres mediciones por configuración)" python3 -c "import sys; sys.exit(0 if $rps_2 >= 1.3 * $rps_1 else 1)"

paso "3. El problema de la sesión local con dos instancias, y su corrección"
CJ="$TRABAJO/n11-cookies.txt"; rm -f "$CJ"
v=""; for i in 1 2 3 4; do v="$v$(curl -s -b "$CJ" -c "$CJ" localhost:$PUERTO_NGINX/api/visitas | jq -r .visitas),"; done
anotar "sesion_local_a_traves_del_balanceador=$v (esperado 1,2,3,4)"
comprobar "con sesión local el contador se pierde al cambiar de instancia" test "$v" != "1,2,3,4,"
docker rm -f $PROYECTO-app1 $PROYECTO-app2 >/dev/null
instancia 1 $PUERTO_APP1 -e SPRING_PROFILES_ACTIVE=redis -e REDIS_HOST=redis -e REDIS_PUERTO=6379
instancia 2 $PUERTO_APP2 -e SPRING_PROFILES_ACTIVE=redis -e REDIS_HOST=redis -e REDIS_PUERTO=6379
docker exec $PROYECTO-redis redis-cli flushall >/dev/null
rm -f "$CJ"; v=""; for i in 1 2 3 4; do v="$v$(curl -s -b "$CJ" -c "$CJ" localhost:$PUERTO_NGINX/api/visitas | jq -r .visitas),"; done
anotar "sesion_en_redis_a_traves_del_balanceador=$v"
comprobar "con la sesión en Redis, cuenta bien (1,2,3,4)" test "$v" = "1,2,3,4,"

paso "5. Operación lenta movida a una cola (envío de correo de 500 ms)"
t_sync=$(for i in 1 2 3 4 5; do curl -s -o /dev/null -w '%{time_total}\n' -XPOST "localhost:$PUERTO_APP1/api/correos/sincrono?demoraMs=500"; done | awk '{s+=$1} END {printf "%d", s/NR*1000}')
t_async=$(for i in 1 2 3 4 5; do curl -s -o /dev/null -w '%{time_total}\n' -XPOST "localhost:$PUERTO_APP1/api/correos/asincrono?demoraMs=500"; done | awk '{s+=$1} END {printf "%d", s/NR*1000}')
anotar "respuesta_media_sincrona_ms=$t_sync respuesta_media_con_cola_ms=$t_async"
comprobar "con la cola la respuesta percibida baja de ~500 ms a unos pocos ms" test "$t_sync" -ge 480 -a "$t_async" -lt 100
sleep 4
comprobar "el trabajo se hizo: la cola envió los 5 correos" test "$(curl -s localhost:$PUERTO_APP1/api/correos/enviados | jq .enviados)" -ge 5
parar_instancias

paso "4. Particionamiento de una tabla de mensajes de chat (en papel, comprobado con EXPLAIN)"
PSQL -d n11_02 -f - < "$AQUI/particionamiento.sql"
por_chat=$(PSQL -d n11_02 -c "EXPLAIN SELECT * FROM mensajes WHERE chat_id = 77 ORDER BY mensaje_id DESC LIMIT 50" | grep -c 'mensajes_p')
por_usuario=$(PSQL -d n11_02 -c "EXPLAIN SELECT * FROM mensajes WHERE usuario_id = 5" | grep -oE 'mensajes_p[0-9]' | sort -u | wc -l | tr -d ' ')
anotar "particiones_tocadas: consulta_por_chat=$por_chat consulta_por_usuario=$por_usuario"
comprobar "la consulta por chat toca una sola partición" test "$por_chat" -ge 1 -a "$(PSQL -d n11_02 -c "EXPLAIN SELECT * FROM mensajes WHERE chat_id = 77" | grep -oE 'mensajes_p[0-9]' | sort -u | wc -l | tr -d ' ')" = 1
comprobar "la consulta por usuario toca las 4: es la que queda difícil" test "$por_usuario" = 4
docker exec $PROYECTO-postgres psql -U postgres -qc "DROP DATABASE n11_02 WITH (FORCE)" >/dev/null

echo; echo "Taller 11-02: todo correcto"
