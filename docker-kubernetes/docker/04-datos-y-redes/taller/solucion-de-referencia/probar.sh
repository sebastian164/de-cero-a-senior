#!/usr/bin/env bash
# Solución ejecutable del taller Docker 04: solo docker run, sin Compose.
# Usa bitacora-api:1.0.0 (sesión 03) y bitacora-web:0.1.0 (sesión 02); las construye si no existen.
# Puertos: 50041 para la web (en el enunciado, 8080) y 50042 para comprobar que la API no está publicada.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
P="$PREFIJO-d04"
API=$PREFIJO-bitacora-api:1.0.0
WEB=$PREFIJO-bitacora-web:0.1.0
RED=$P-red
VOL=$P-notas
OUT="$SALIDAS/docker-04.txt"; : > "$OUT"
T04="$(mktemp -d "${TMPDIR:-/tmp}/$PREFIJO-d04.XXXXXX")"
# La web corre sin root: uid 101 (nginx) y tres tmpfs donde nginx necesita escribir.
WEB_SIN_ROOT=(--user 101:101 --tmpfs /var/cache/nginx:mode=1777 --tmpfs /run:mode=1777 --tmpfs /etc/nginx/conf.d:mode=1777)

limpiar() {
  docker rm -fv $P-api $P-api2 $P-web $P-web-bridge $P-api-bridge >/dev/null 2>&1
  docker network rm $RED >/dev/null 2>&1; docker volume rm $VOL >/dev/null 2>&1
}
limpiar
asegurar_bitacora api-1.0.0
asegurar_bitacora web-0.1.0

notas() { docker run --rm --network $RED curlimages/curl:8.17.0 -s "http://$1:8080/api/notas"; }

titulo "Docker 04, paso 1: el problema"
docker network create $RED >/dev/null
docker run -d --name $P-api --network $RED $API >/dev/null
esperar_hasta 30 sh -c "docker inspect -f '{{.State.Health.Status}}' $P-api | grep -qx healthy"
docker run --rm --network $RED curlimages/curl:8.17.0 -s -X POST --data 'nota sin volumen' http://$P-api:8080/api/notas >/dev/null
docker rm -f $P-api >/dev/null
docker run -d --name $P-api --network $RED $API >/dev/null
esperar_hasta 30 sh -c "docker inspect -f '{{.State.Health.Status}}' $P-api | grep -qx healthy"
r="$(notas $P-api)"; nota "tras recrear: $r"
afirmar "lista vacía tras recrear sin volumen" sh -c "echo '$r' | grep -q '\"notas\":\[\]'"
docker rm -f $P-api >/dev/null

titulo "Pasos 2 y 3: volumen con nombre y permisos"
docker volume create $VOL >/dev/null
docker run -d --name $P-api --network $RED -v $VOL:/datos $API >/dev/null
esperar_hasta 30 sh -c "docker inspect -f '{{.State.Health.Status}}' $P-api | grep -qx healthy"
docker run --rm --network $RED curlimages/curl:8.17.0 -s -X POST --data 'nota con volumen' http://$P-api:8080/api/notas >/dev/null
docker rm -f $P-api >/dev/null
docker run -d --name $P-api --network $RED -v $VOL:/datos $API >/dev/null
esperar_hasta 30 sh -c "docker inspect -f '{{.State.Health.Status}}' $P-api | grep -qx healthy"
r="$(notas $P-api)"; nota "tras docker rm -f y recrear: $r"
afirmar "la nota sobrevive a docker rm -f" sh -c "echo '$r' | grep -q 'nota con volumen'"
nota "dueño de /datos en el volumen: $(docker exec $P-api stat -c '%u:%g' /datos); usuario del proceso: $(docker exec $P-api id -u)"
# Contraprueba del paso 3: una imagen sin /datos preparado da error de permisos.
cat > $T04/sinpermisos.Dockerfile <<EOF
FROM $API
USER root
RUN rm -rf /datos
USER 10001:10001
EOF
docker build -q -f $T04/sinpermisos.Dockerfile -t $PREFIJO-d04-sinpermisos "$T04" >/dev/null
docker run -d --name $P-api2 -v $P-otro:/datos $PREFIJO-d04-sinpermisos >/dev/null
esperar_hasta 20 sh -c "docker logs $P-api2 2>&1 | grep -q 'ERROR no puedo escribir'"
nota "sin /datos en la imagen el volumen nace de root: $(docker logs $P-api2 2>&1 | grep -m1 ERROR | cut -c32-150)"
afirmar "contraprueba: sin preparar /datos en la imagen, la API no queda lista" sh -c "docker logs $P-api2 2>&1 | grep -q 'AccessDenied'"
docker rm -fv $P-api2 >/dev/null; docker volume rm $P-otro >/dev/null; docker rmi $PREFIJO-d04-sinpermisos >/dev/null; rm -f $T04/sinpermisos.Dockerfile

titulo "Paso 4: red por defecto, sin resolución de nombres"
docker run -d --name $P-api-bridge $API >/dev/null
docker run -d --name $P-web-bridge "${WEB_SIN_ROOT[@]}" -e DESTINO_API=http://$P-api-bridge:8080 $WEB >/dev/null
esperar_hasta 30 sh -c "[ \"\$(docker inspect -f '{{.State.Status}}' $P-web-bridge)\" = exited ]"   # bajo carga nginx tarda más de 3 s en fallar
igual "en la red bridge la web no arranca" exited "$(docker inspect -f '{{.State.Status}}' $P-web-bridge)"
err="$(docker logs $P-web-bridge 2>&1 | grep -m1 'host not found')"; nota "$err"
echo "bridge: $err" >> "$OUT"
docker rm -f $P-api-bridge $P-web-bridge >/dev/null

titulo "Pasos 5 y 6: red propia, solo la web publicada"
docker run -d --name $P-web --network $RED -p 50041:8080 "${WEB_SIN_ROOT[@]}" -e DESTINO_API=http://$P-api:8080 $WEB >/dev/null
esperar_hasta 20 curl -sf localhost:50041/
afirmar "la página carga" sh -c "curl -s localhost:50041/ | grep -q '<title>Bitácora</title>'"
igual "a través de la web: /api/info" 200 "$(codigo_http localhost:50041/api/info)"
igual "a través de la web: POST /api/notas" 201 "$(codigo_http -X POST --data 'desde la web' localhost:50041/api/notas)"
afirmar "a través de la web se listan las notas" sh -c "curl -s localhost:50041/api/notas | grep -q 'desde la web'"
negar "la API no es alcanzable desde el equipo (no publica puertos)" curl -sf -m 3 localhost:50042/api/info
igual "la API no tiene puertos publicados" "{}" "$(docker inspect -f '{{json .NetworkSettings.Ports}}' $P-api | python3 -c 'import json,sys; print({k:v for k,v in json.load(sys.stdin).items() if v})')"
nota "la web resuelve la API por nombre: $(docker exec $P-web getent hosts $P-api)"
igual "ningún contenedor como root: api" 10001 "$(docker exec $P-api id -u)"
igual "ningún contenedor como root: web" 101 "$(docker exec $P-web id -u)"

titulo "Paso 7: segunda API sobre el mismo volumen"
docker run -d --name $P-api2 --network $RED -v $VOL:/datos -e MENSAJE='Soy la segunda' $API >/dev/null
esperar_hasta 30 sh -c "docker inspect -f '{{.State.Health.Status}}' $P-api2 | grep -qx healthy"
docker run --rm --network $RED curlimages/curl:8.17.0 -s -X POST --data 'escrita por api' http://$P-api:8080/api/notas >/dev/null
docker run --rm --network $RED curlimages/curl:8.17.0 -s -X POST --data 'escrita por api2' http://$P-api2:8080/api/notas >/dev/null
a="$(notas $P-api)"; b="$(notas $P-api2)"
nota "api : $a"; nota "api2: $b"
afirmar "las dos copias leen el mismo archivo" sh -c "echo '$a' | grep -q 'escrita por api2' && echo '$b' | grep -q 'escrita por api\"'"
# Escrituras concurrentes desde los dos procesos: 200 notas cada uno.
docker run --rm --network $RED curlimages/curl:8.17.0 sh -c "
  for i in \$(seq 1 200); do curl -s -o /dev/null -X POST --data \"a-\$i-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\" http://$P-api:8080/api/notas & curl -s -o /dev/null -X POST --data \"b-\$i-yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy\" http://$P-api2:8080/api/notas; wait; done"
total="$(docker exec $P-api sh -c 'wc -l < /datos/notas.txt')"
rotas="$(docker exec $P-api sh -c "grep -cvE '^(nota con volumen|desde la web|escrita por api2?|a-[0-9]+-x{32}|b-[0-9]+-y{32})\$' /datos/notas.txt" || true)"
nota "tras 400 escrituras concurrentes: $total líneas, $rotas mezcladas"
echo "concurrencia: lineas=$total mezcladas=$rotas" >> "$OUT"
docker rm -f $P-api2 >/dev/null

titulo "Paso 8: montaje de carpeta sobre index.html"
mkdir -p $T04/web && printf '<!DOCTYPE html><title>Bitácora</title><h1>Editado sin reconstruir</h1>\n' > $T04/web/index.html
docker rm -f $P-web >/dev/null
docker run -d --name $P-web --network $RED -p 50041:8080 "${WEB_SIN_ROOT[@]}" -e DESTINO_API=http://$P-api:8080 \
  -v $T04/web/index.html:/usr/share/nginx/html/index.html:ro $WEB >/dev/null
esperar_hasta 20 curl -sf localhost:50041/
afirmar "la página servida es la de la carpeta del equipo" sh -c "curl -s localhost:50041/ | grep -q 'Editado sin reconstruir'"
printf '<!DOCTYPE html><title>Bitácora</title><h1>Segundo cambio en caliente</h1>\n' > $T04/web/index.html
afirmar "un cambio en el equipo se ve al instante" sh -c "curl -s localhost:50041/ | grep -q 'Segundo cambio'"

titulo "Paso 9: limpieza"
limpiar; rm -rf "$T04"
igual "sin contenedores" "" "$(docker ps -a --filter name=$P- --format '{{.Names}}')"
igual "sin red" "" "$(docker network ls --filter name=$RED -q)"
igual "sin volumen" "" "$(docker volume ls --filter name=$VOL -q)"
terminar "Docker 04"
