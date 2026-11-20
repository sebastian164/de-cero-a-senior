#!/usr/bin/env bash
# Solución ejecutable del taller Docker 05. Trabaja sobre una copia limpia de esta carpeta (como un clon nuevo)
# a la que añade el código de docker-kubernetes/app.
# Puertos: 50051 y 50052 para las dos copias; 50053 y 50054 para reproducir el choque del archivo de inicio.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
CLON="$(mktemp -d "${TMPDIR:-/tmp}/$PREFIJO-d05.XXXXXX")"
OUT="$SALIDAS/docker-05.txt"; : > "$OUT"
A=$PREFIJO-d05a; B=$PREFIJO-d05b; I1=$PREFIJO-d05i1; I2=$PREFIJO-d05i2

# Copia limpia: lo que habría en el repositorio, sin .env ni secretos.
montar_solucion "$CLON" app
cd "$CLON"
dc() { docker compose -p "$@"; }
limpiar() {
  for p in $A $B; do dc $p --profile datos down -v --remove-orphans >/dev/null 2>&1; done
  for p in $I1 $I2; do docker compose -p $p -f "$CLON/inicio.yaml" down -v >/dev/null 2>&1; done
  docker rmi -f $A-api $A-web $B-api $B-web $PREFIJO-d05-api:1.0.0 $PREFIJO-d05-web:0.1.0 >/dev/null 2>&1
}
limpiar

titulo "Docker 05, criterio: un clon limpio levanta con una orden (cp .env.ejemplo .env && docker compose up -d --build)"
cp .env.ejemplo .env
nota "orden exacta: PUERTO_WEB=50051 docker compose -p $A up -d --build --wait   (PUERTO_WEB solo evita el 8080, por si lo tienes ocupado)"
afirmar "docker compose config valida el archivo" dc $A config -q
dc $A config > "$SALIDAS/docker-05-config.yaml"
if PUERTO_WEB=50051 dc $A up -d --build --wait >/dev/null 2>&1; then ok "el clon limpio levanta con una orden, sin ningún guion previo"; else mal "up falló en el clon limpio"; dc $A logs | tail -20; fi
estados="$(dc $A ps --format '{{.Service}}={{.Health}}' | sort | tr '\n' ' ')"
igual "todos los servicios healthy" "api=healthy web=healthy " "$estados"
igual "con la clave de ejemplo de .env.ejemplo, POST con X-Clave" 201 "$(codigo_http -X POST -H "X-Clave: clave-de-ejemplo-cambiala" --data 'nota de ejemplo' localhost:50051/api/notas)"
# A partir de aquí, claves aleatorias en .env (como haría cada persona en su equipo).
dc $A down -v >/dev/null 2>&1
CLAVE="$(openssl rand -hex 16)"
sed_i "s/^CLAVE_API=.*/CLAVE_API=$CLAVE/; s/^CLAVE_DB=.*/CLAVE_DB=$(openssl rand -hex 16)/; s/^PUERTO_WEB=.*/PUERTO_WEB=50051/" .env
dc $A up -d --build --wait >/dev/null 2>&1 || { mal "up con claves propias"; dc $A logs | tail -20; }
estados="$(dc $A ps --format '{{.Service}}={{.Health}}' | sort | tr '\n' ' ')"
igual "todos los servicios healthy con claves propias" "api=healthy web=healthy " "$estados"
afirmar "la página funciona completa" sh -c "curl -s localhost:50051/ | grep -q '<title>Bitácora</title>' && curl -sf localhost:50051/api/info"
nota "$(curl -s localhost:50051/api/info)"

titulo "Paso 3: la API no está publicada"
negar "docker compose port api 8080 no da un puerto del anfitrión (imprime :0)" sh -c "docker compose -p $A port api 8080 | grep -q ':[1-9]'"
igual "la API no tiene puertos publicados en el anfitrión" "" "$(docker inspect -f '{{range $p, $c := .NetworkSettings.Ports}}{{if $c}}{{$p}}{{end}}{{end}}' $A-api-1)"
nota "(se comprueba que la API no publica ningún puerto en el equipo)"

titulo "Paso 6: CLAVE_API como secreto"
igual "POST sin cabecera" 401 "$(codigo_http -X POST --data 'sin clave' localhost:50051/api/notas)"
igual "POST con X-Clave" 201 "$(codigo_http -X POST -H "X-Clave: $CLAVE" --data 'nota persistente' localhost:50051/api/notas)"
negar "el valor de la clave real no aparece en ningún archivo versionable" grep -rqF "$CLAVE" compose.yaml .env.ejemplo .gitignore app
negar "la clave no aparece en docker inspect (Config.Env)" sh -c "docker inspect -f '{{json .Config.Env}}' $A-api-1 | grep -qF '$CLAVE'"
afirmar "el secreto está montado como archivo en /run/secrets/clave_api" sh -c "docker exec $A-api-1 cat /run/secrets/clave_api | grep -qxF '$CLAVE'"
afirmar "limitación: quien puede hacer docker exec la lee en /proc/1/environ (la imagen la exporta a Java)" sh -c "docker exec $A-api-1 cat /proc/1/environ | tr '\0' '\n' | grep -qF '$CLAVE'"
afirmar ".gitignore cubre .env" grep -qx '.env' .gitignore

titulo "Paso 4: las notas sobreviven a down y up"
dc $A down >/dev/null 2>&1
dc $A up -d --wait >/dev/null 2>&1
afirmar "la nota sigue tras down + up" sh -c "curl -s localhost:50051/api/notas | grep -q 'nota persistente'"

titulo "Paso 5: la web espera a que la API esté sana (RETARDO_ARRANQUE_MS=15000)"
dc $A down >/dev/null 2>&1
RETARDO_ARRANQUE_MS=15000 dc $A up -d >/dev/null 2>&1 &
UP=$!
inicio=$(date +%s); muestras=""
for s in 3 8 13; do
  while [ $(( $(date +%s) - inicio )) -lt $s ]; do sleep 0.5; done
  w="$(docker inspect -f '{{.State.Status}}' $A-web-1 2>/dev/null || echo ausente)"
  a="$(docker inspect -f '{{.State.Health.Status}}' $A-api-1 2>/dev/null || echo ausente)"
  muestras="$muestras ${s}s:api=$a,web=$w"
done
wait $UP
nota "muestras:$muestras"
negar "mientras la API arranca la web no está en marcha" sh -c "echo '$muestras' | grep -q 'web=running'"
esperar_hasta 30 sh -c "docker inspect -f '{{.State.Health.Status}}' $A-web-1 | grep -qx healthy"
igual "al final la web queda healthy" healthy "$(docker inspect -f '{{.State.Health.Status}}' $A-web-1)"
echo "arranque_lento:$muestras" >> "$OUT"

titulo "Paso 8: perfil datos"
igual "sin perfil no hay db" "" "$(dc $A ps -q db)"
dc $A --profile datos up -d --wait db >/dev/null 2>&1
igual "con --profile datos la db llega a healthy" healthy "$(docker inspect -f '{{.State.Health.Status}}' $A-db-1)"
afirmar "la db acepta la contraseña del secreto" docker exec -e PGPASSWORD="$(grep '^CLAVE_DB=' .env | cut -d= -f2)" $A-db-1 psql -h 127.0.0.1 -U postgres -tAc 'select 1'

titulo "Paso 10: límites de la API"
igual "límite de memoria (bytes)" 134217728 "$(docker inspect -f '{{.HostConfig.Memory}}' $A-api-1)"
igual "límite de CPU (nanoCPUs)" 500000000 "$(docker inspect -f '{{.HostConfig.NanoCpus}}' $A-api-1)"

titulo "Paso 9: dos copias a la vez"
cat > inicio.yaml <<EOF
services:
  api:
    image: $PREFIJO-bitacora-api:1.0.0
    ports:
      - "50053:8080"
  web:
    image: $PREFIJO-bitacora-web:0.1.0
    ports:
      - "50054:8080"
EOF
nota "archivo de inicio con los puertos fijos cambiados a 50053/50054:"
docker compose -p $I1 -f inicio.yaml up -d >/dev/null 2>&1
if docker compose -p $I2 -f inicio.yaml up -d > "$CLON/choque.log" 2>&1; then mal "el archivo de inicio no debería admitir dos copias"; else ok "la segunda copia del archivo de inicio choca: $(grep -o 'port is already allocated\|address already in use' "$CLON/choque.log" | head -1)"; fi
docker compose -p $I2 -f inicio.yaml down >/dev/null 2>&1; docker compose -p $I1 -f inicio.yaml down >/dev/null 2>&1
PUERTO_WEB=50052 MENSAJE="Segunda copia" CLAVE_API=$CLAVE CLAVE_DB=x dc $B up -d --build --wait >/dev/null 2>&1 && ok "segunda copia levantada con -p $B y PUERTO_WEB=50052" || mal "la segunda copia no levanta"
afirmar "copia 1 responde" curl -sf localhost:50051/api/info
afirmar "copia 2 responde con su mensaje" sh -c "curl -s localhost:50052/api/info | grep -q 'Segunda copia'"
negar "las copias no comparten notas (volúmenes por proyecto)" sh -c "curl -s localhost:50052/api/notas | grep -q 'nota persistente'"
nota "volúmenes: $(docker volume ls -q --filter name=$PREFIJO-d05 | tr '\n' ' ')"

titulo "Paso 11: docker compose config"
nota "salida de docker compose config: $(wc -l < "$SALIDAS/docker-05-config.yaml" | tr -d ' ') líneas"

limpiar
cd /; rm -rf "$CLON"
terminar "Docker 05"
