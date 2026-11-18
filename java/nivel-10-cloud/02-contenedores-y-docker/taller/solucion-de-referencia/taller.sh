#!/usr/bin/env bash
# Solución del taller de la sesión 02 (Contenedores y Docker): una etapa frente a varias, Compose con aplicación,
# PostgreSQL y Kafka, memoria del contenedor, Trivy y la misma imagen con dos configuraciones.
# Levanta lo que necesita y lo baja todo al terminar.
# Variables opcionales: PROYECTO (prefijo de nombres, minúsculas; por defecto n10-02), PUERTO_COMPOSE (8080), PUERTO_MEM (8081),
# PUERTO_PRUEBAS (8082), PUERTO_PRODUCCION (8083), TRIVY_CACHE (carpeta de las bases de Trivy).
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; source "$AQUI/comun.sh"
requiere docker trivy curl jq python3 rsync
cd "$AQUI"
export PROYECTO=${PROYECTO:-n10-02}
export PUERTO_COMPOSE=${PUERTO_COMPOSE:-8080} PUERTO_MEM=${PUERTO_MEM:-8081} PUERTO_PRUEBAS=${PUERTO_PRUEBAS:-8082} PUERTO_PRODUCCION=${PUERTO_PRODUCCION:-8083}
export TRIVY_CACHE=${TRIVY_CACHE:-$HOME/.cache/curso-trivy}; mkdir -p "$TRIVY_CACHE"
export IMAGEN_APP=$PROYECTO/pedidos:compose  # nombre de la imagen que construye compose.yaml
APP=$AQUI/aplicacion
TRABAJO=$(mktemp -d); TMP=$TRABAJO/trabajo; mkdir -p "$TMP"
RESULTADOS=$TRABAJO/resultados.txt; : > "$RESULTADOS"
anotar() { echo "$*" | tee -a "$RESULTADOS" | sed 's/^/    /'; }
tamano_mb() { echo $(( $(docker image inspect "$1" --format '{{.Size}}') / 1000000 )); }
copia() { rsync -a --exclude target "$APP/" "$1/"; }
cambiar_una_linea() { echo "// cambio $(date +%s%N)" >> "$1/src/main/java/pedidos/AplicacionPedidos.java"; }
cronometrar() { local t0; t0=$(date +%s); "$@" >/dev/null 2>&1; echo $(( $(date +%s) - t0 )); }
bajar() {
  docker compose -p "$PROYECTO-taller02" down -v --remove-orphans >/dev/null 2>&1 || true
  docker rm -f "$PROYECTO-mem" "$PROYECTO-pruebas" "$PROYECTO-produccion" "$PROYECTO-db" >/dev/null 2>&1 || true
  docker network rm "$PROYECTO-red" >/dev/null 2>&1 || true
  docker image rm "$PROYECTO/pedidos:una-etapa" "$PROYECTO/pedidos:varias-etapas" "$PROYECTO/pedidos:trivy" "$IMAGEN_APP" >/dev/null 2>&1 || true
  rm -rf "$TRABAJO"
}
trap bajar EXIT

paso "1. Dockerfile de una sola etapa"
copia "$TMP/una"
docker build -q -t $PROYECTO/pedidos:una-etapa -f "$AQUI/Dockerfile.una-etapa" "$TMP/una" >/dev/null
una=$(tamano_mb $PROYECTO/pedidos:una-etapa)
anotar "tamano_una_etapa_mb=$una"

paso "2. Varias etapas, usuario sin privilegios y capas ordenadas (el Dockerfile del README)"
copia "$TMP/varias"
docker build -q -t $PROYECTO/pedidos:varias-etapas "$TMP/varias" >/dev/null
varias=$(tamano_mb $PROYECTO/pedidos:varias-etapas)
anotar "tamano_varias_etapas_mb=$varias"
comprobar "la imagen de varias etapas es más pequeña ($varias MB < $una MB)" test "$varias" -lt "$una"
comprobar "la imagen final no lleva javac (solo JRE)" bash -c '! docker run --rm --entrypoint sh $PROYECTO/pedidos:varias-etapas -c "command -v javac"'
comprobar "la imagen final no lleva el código fuente" bash -c '! docker run --rm --entrypoint sh $PROYECTO/pedidos:varias-etapas -c "test -d /app/src"'
comprobar "la final corre como 'aplicacion', la de una etapa como root" bash -c '[ "$(docker run --rm --entrypoint id $PROYECTO/pedidos:varias-etapas -un)" = aplicacion ] && [ "$(docker run --rm --entrypoint id $PROYECTO/pedidos:una-etapa -un)" = root ]'
cambiar_una_linea "$TMP/una"; cambiar_una_linea "$TMP/varias"
t_una=$(cronometrar docker build -t $PROYECTO/pedidos:una-etapa -f "$AQUI/Dockerfile.una-etapa" "$TMP/una")
t_varias=$(cronometrar docker build -t $PROYECTO/pedidos:varias-etapas "$TMP/varias")
anotar "reconstruccion_tras_cambiar_una_linea_una_etapa_s=$t_una"
anotar "reconstruccion_tras_cambiar_una_linea_varias_etapas_s=$t_varias"
comprobar "con la capa de dependencias en caché, reconstruir es más rápido ($t_varias s < $t_una s)" test "$t_varias" -lt "$t_una"

paso "3. Docker Compose: aplicación, PostgreSQL y Kafka con comprobaciones de salud"
export CLAVE_BD="local-$RANDOM"
docker compose -p "$PROYECTO-taller02" up -d --wait >/dev/null 2>&1
comprobar "los tres servicios están healthy" bash -c "[ \$(docker compose -p $PROYECTO-taller02 ps --format '{{.Health}}' | grep -c healthy) = 3 ]"
id=$(curl -sf -XPOST localhost:$PUERTO_COMPOSE/api/pedidos -H 'Content-Type: application/json' -d '{"clienteId":7,"total":42.50}' | jq .id)
comprobar "el pedido $id se guarda en PostgreSQL" bash -c "docker compose -p $PROYECTO-taller02 exec -T db psql -U postgres -d tienda -tAc 'select total from pedidos where id=$id' | grep -qx 42.50"
comprobar "y llega al tópico pedidos-creados de Kafka" bash -c "docker compose -p $PROYECTO-taller02 exec -T broker /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic pedidos-creados --from-beginning --max-messages 1 --timeout-ms 20000 2>/dev/null | grep -q '\"pedidoId\":$id'"
docker compose -p "$PROYECTO-taller02" down -v >/dev/null 2>&1

paso "4. Memoria del contenedor por debajo de lo que la aplicación necesita"
IMG=$PROYECTO/pedidos:varias-etapas
docker rm -f $PROYECTO-mem $PROYECTO-db >/dev/null 2>&1 || true
docker network rm $PROYECTO-red >/dev/null 2>&1 || true
docker network create $PROYECTO-red >/dev/null
docker run -d --name $PROYECTO-db --network $PROYECTO-red --network-alias db -e POSTGRES_PASSWORD="$CLAVE_BD" postgres:16 >/dev/null
for i in $(seq 1 30); do docker exec $PROYECTO-db pg_isready -U postgres >/dev/null 2>&1 && break; sleep 1; done
sleep 2
for bd in tienda pruebas produccion; do docker exec $PROYECTO-db psql -U postgres -qc "create database $bd" >/dev/null; done
BD="--network $PROYECTO-red -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/tienda -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=$CLAVE_BD"
# -Xmx fijo de 1 GB dentro de un contenedor de 300 MB: el montón puede crecer más que el contenedor.
docker run -d --name $PROYECTO-mem -m 300m -p 127.0.0.1:$PUERTO_MEM:8080 -e JAVA_TOOL_OPTIONS=-Xmx1g $BD "$IMG" >/dev/null
esperar_http http://localhost:$PUERTO_MEM/api/info 180 || falla "la aplicación no arrancó en 3 minutos"
curl -s -m 60 -XPOST "localhost:$PUERTO_MEM/api/memoria?mb=600" >/dev/null || true
sleep 2
estado=$(docker inspect $PROYECTO-mem --format '{{.State.Status}} {{.State.OOMKilled}} {{.State.ExitCode}}')
anotar "con_Xmx1g_y_300m: estado,OOMKilled,codigo=$estado"
comprobar "el núcleo mata el contenedor sin excepción de Java (OOMKilled=true, 137)" test "$estado" = "exited true 137"
docker rm -f $PROYECTO-mem >/dev/null
# Ajuste: sin -Xmx; montón como porcentaje del límite. Con 300 MB, el 75 % del README deja ~75 MB para
# metaspace, hilos y código compilado, que no alcanzan: se usa el 50 % (150 MB de montón).
docker run -d --name $PROYECTO-mem -m 300m -p 127.0.0.1:$PUERTO_MEM:8080 $BD --entrypoint java "$IMG" -XX:MaxRAMPercentage=50 -jar app.jar >/dev/null
esperar_http http://localhost:$PUERTO_MEM/api/info 180
maximo=$(curl -s localhost:$PUERTO_MEM/api/info | jq .memoriaMaximaMb)
codigo=$(curl -s -m 60 -o /dev/null -w '%{http_code}' -XPOST "localhost:$PUERTO_MEM/api/memoria?mb=600" || true)
curl -s -XDELETE localhost:$PUERTO_MEM/api/memoria >/dev/null || true
estado=$(docker inspect $PROYECTO-mem --format '{{.State.Status}} {{.State.OOMKilled}}')
anotar "con_MaxRAMPercentage50_y_300m: montón=${maximo}MB peticion=$codigo estado=$estado"
comprobar "la JVM ajusta el montón al límite (≈150 MB, fue $maximo)" test "$maximo" -ge 130 -a "$maximo" -le 155
comprobar "ahora falla la petición (500, OutOfMemoryError) y el contenedor sigue vivo" test "$codigo $estado" = "500 running false"
{ docker logs $PROYECTO-mem 2>&1 | grep -o 'java.lang.OutOfMemoryError: Java heap space' | head -1 | sed 's/^/    log: /'; } || true
docker rm -f $PROYECTO-mem >/dev/null

paso "5. Vulnerabilidades con Trivy y corrección de un hallazgo"
trivy_altas() { trivy image -q --cache-dir "$TRIVY_CACHE" --scanners vuln --severity HIGH,CRITICAL --format json "$1" \
  | jq -r '[.Results[]? | (.Vulnerabilities // [])[] | "\(.PkgName)@\(.InstalledVersion) \(.VulnerabilityID)"] | unique | .[]'; }
# El pom del proyecto ya lleva el parche (snappy-java y lz4-java corregidos). El "antes" vulnerable se construye
# quitándole el parche en una copia temporal; el "después" es la imagen de varias etapas ya construida.
copia "$TMP/trivy"
python3 - "$TMP/trivy/pom.xml" "$AQUI/pom-parche-trivy.xml" <<'PY'
import sys
pom, parche = open(sys.argv[1]).read(), open(sys.argv[2]).read()
assert parche in pom
open(sys.argv[1], "w").write(pom.replace(parche, "", 1))
PY
docker build -q -t $PROYECTO/pedidos:trivy "$TMP/trivy" >/dev/null
antes=$(trivy_altas $PROYECTO/pedidos:trivy)
echo "$antes" | sed 's/^/    antes: /'
despues=$(trivy_altas "$IMG")
echo "${despues:-(ninguno)}" | sed 's/^/    después: /'
anotar "hallazgos_altos_antes=$(echo "$antes" | grep -c . || true) despues=$(echo "$despues" | grep -c . || true)"
comprobar "había hallazgos de snappy-java o lz4-java" bash -c "echo '$antes' | grep -qE 'snappy-java|lz4-java'"
comprobar "con las versiones corregidas en el pom desaparecen" bash -c "! echo '$despues' | grep -qE 'snappy-java|lz4-java'"

paso "6. La misma imagen con dos configuraciones"
for amb in pruebas produccion; do
  docker rm -f "$PROYECTO-$amb" >/dev/null 2>&1 || true
done
docker run -d --name $PROYECTO-pruebas -p 127.0.0.1:$PUERTO_PRUEBAS:8080 --network $PROYECTO-red \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/pruebas -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD="$CLAVE_BD" \
  -e APP_AMBIENTE=pruebas -e APP_MENSAJE=hola-pruebas "$IMG" >/dev/null
docker run -d --name $PROYECTO-produccion -p 127.0.0.1:$PUERTO_PRODUCCION:8080 --network $PROYECTO-red \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/produccion -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD="$CLAVE_BD" \
  -e APP_AMBIENTE=produccion -e APP_MENSAJE=hola-produccion "$IMG" >/dev/null
esperar_http http://localhost:$PUERTO_PRUEBAS/api/info 180; esperar_http http://localhost:$PUERTO_PRODUCCION/api/info 180
a=$(curl -s localhost:$PUERTO_PRUEBAS/api/info | jq -r '.ambiente+"/"+.mensaje'); b=$(curl -s localhost:$PUERTO_PRODUCCION/api/info | jq -r '.ambiente+"/"+.mensaje')
anotar "misma_imagen: $a | $b"
comprobar "los dos contenedores usan la misma imagen (mismo ID)" test "$(docker inspect $PROYECTO-pruebas --format '{{.Image}}')" = "$(docker inspect $PROYECTO-produccion --format '{{.Image}}')"
comprobar "y responden con la configuración de su entorno" test "$a|$b" = "pruebas/hola-pruebas|produccion/hola-produccion"
comprobar "cada una con su propia base (pedido creado solo en pruebas)" bash -c 'curl -sf -XPOST localhost:$PUERTO_PRUEBAS/api/pedidos -H "Content-Type: application/json" -d "{\"clienteId\":1,\"total\":1}" >/dev/null && [ "$(docker exec $PROYECTO-db psql -U postgres -d pruebas -tAc "select count(*) from pedidos")" = 1 ] && [ "$(docker exec $PROYECTO-db psql -U postgres -d produccion -tAc "select count(*) from pedidos")" = 0 ]'
docker rm -f $PROYECTO-pruebas $PROYECTO-produccion $PROYECTO-db >/dev/null
docker network rm $PROYECTO-red >/dev/null
echo; echo "Taller 02: todo correcto"
