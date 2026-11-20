#!/usr/bin/env bash
# Solución ejecutable del taller Docker 03. Parte de la imagen 0.1.0 de la sesión 02 (la construye si no existe).
# Deja construida ${PREFIJO}-bitacora-api:1.0.0 (se borra al terminar, salvo con MANTENER=1).
# Puertos del equipo: 50031 a 50034.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
MONTAJE="$(mktemp -d "${TMPDIR:-/tmp}/$PREFIJO-montaje.XXXXXX")"; montar_solucion "$MONTAJE"   # esta carpeta + el código de docker-kubernetes/app
SOL="$MONTAJE/api"
P="$PREFIJO-d03"
V01=$PREFIJO-bitacora-api:0.1.0
V1=$PREFIJO-bitacora-api:1.0.0
JRE=$PREFIJO-d03-jre:1.0.0
TMP="$(mktemp -d "${TMPDIR:-/tmp}/$PREFIJO-d03.XXXXXX")"
OUT="$SALIDAS/docker-03.txt"; : > "$OUT"
limpiar() { docker rm -fv $P-a $P-b $P-c $P-lento $P-ro $P-mem $P-mem2 $P-jre >/dev/null 2>&1; docker volume rm $P-datos >/dev/null 2>&1; }
limpiar
asegurar_bitacora api-0.1.0

mb() { echo $(( $(docker image inspect -f '{{.Size}}' "$1") / 1000000 )); }
reconstruccion() { # mide la reconstrucción tras cambiar una línea de código
  rm -rf "$TMP/r"; cp -R "$SOL" "$TMP/r"; cp "$1" "$TMP/r/Dockerfile" 2>/dev/null || true
  docker build -q -t $PREFIJO-d03-r "$TMP/r" >/dev/null
  echo "// cambio $(date +%s%N)" >> "$TMP/r/Servidor.java"
  local i; i=$(ahora_ms); docker build -q -t $PREFIJO-d03-r "$TMP/r" >/dev/null; echo $(( $(ahora_ms) - i ))
}

titulo "Docker 03, paso 1: punto de partida (0.1.0)"
docker run -d --name $P-a $V01 >/dev/null; sleep 2
id01="$(docker exec $P-a id)"
t01=$(reconstruccion "$SESION02/api/Dockerfile")
nota "tamaño $(mb $V01) MB | usuario: $id01 | reconstrucción tras cambiar una línea: ${t01} ms"
echo "inicio: tam=$(mb $V01)MB id='$id01' reconstruccion=${t01}ms" >> "$OUT"
docker rm -f $P-a >/dev/null

titulo "Pasos 2 a 4 (variante JRE de Ubuntu) y paso 8 (jlink sobre Alpine)"
docker build -q -f "$SOL/Dockerfile.jre" -t $JRE "$SOL" >/dev/null && ok "variante JRE construida" || mal "variante JRE no construye"
i=$(ahora_ms); docker build -q --no-cache -t $V1 "$SOL" >/dev/null && ok "bitacora-api:1.0.0 construida desde cero en $(( $(ahora_ms) - i )) ms" || mal "1.0.0 no construye"
t1=$(reconstruccion "$SOL/Dockerfile")
nota "tamaños: 0.1.0 = $(mb $V01) MB, JRE = $(mb $JRE) MB, 1.0.0 (jlink + Alpine) = $(mb $V1) MB; reconstrucción 1.0.0: ${t1} ms"
echo "tam: v01=$(mb $V01)MB jre=$(mb $JRE)MB v1=$(mb $V1)MB reconstruccion_v1=${t1}ms" >> "$OUT"
for img in $JRE $V1; do
  salida="$(docker run --rm --entrypoint sh $img -c 'command -v javac' 2>&1)"
  igual "$img: command -v javac no devuelve nada" "" "$salida"
  afirmar "$img: Servidor.java no está en la imagen" sh -c "! docker run --rm --entrypoint sh $img -c 'find / -name Servidor.java 2>/dev/null' | grep -q ."
done
nota "herramientas en la imagen final: $(docker run --rm --entrypoint sh $V1 -c 'for h in wget curl nc; do command -v $h; done' | tr '\n' ' ')"

titulo "Paso 3: usuario sin privilegios"
docker run -d --name $P-b -p 50031:8080 $V1 >/dev/null
esperar_hasta 30 curl -sf localhost:50031/salud/listo
idv1="$(docker exec $P-b id)"; nota "$idv1"
igual "el proceso corre con uid 10001" 10001 "$(docker exec $P-b id -u)"
igual "puede escribir en DIRECTORIO_DATOS sin ser root" 201 "$(codigo_http -X POST --data 'nota 03' localhost:50031/api/notas)"

titulo "Paso 7 y criterio: parada en menos de 4 s"
i=$(ahora_ms); docker stop -t 10 $P-b >/dev/null; t_stop=$(( $(ahora_ms) - i ))
afirmar "docker stop -t 10 tardó ${t_stop} ms" test "$t_stop" -lt 4000

titulo "Paso 5: arranque lento y salud"
inicio=$(date +%s)
docker run -d --name $P-lento -e RETARDO_ARRANQUE_MS=15000 $V1 >/dev/null
estados=""
for s in 2 8 14 20 26; do
  while [ $(( $(date +%s) - inicio )) -lt $s ]; do sleep 0.5; done
  e="$(docker inspect -f '{{.State.Health.Status}}' $P-lento)"; estados="$estados ${s}s:$e"
done
nota "estado de salud con arranque de 15 s:$estados"
esperar_hasta 40 sh -c "docker inspect -f '{{.State.Health.Status}}' $P-lento | grep -q healthy"
igual "llega a healthy con arranque lento" healthy "$(docker inspect -f '{{.State.Health.Status}}' $P-lento)"
negar "nunca pasó por unhealthy" sh -c "echo '$estados' | grep -q unhealthy"
echo "salud_lento:$estados" >> "$OUT"
cat > "$TMP/sin-margen.Dockerfile" <<EOF
FROM $V1
HEALTHCHECK --interval=2s --timeout=2s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/salud/listo || exit 1
EOF
docker build -q -f "$TMP/sin-margen.Dockerfile" -t $PREFIJO-d03-sinmargen "$TMP" >/dev/null
docker run -d --name $P-c -e RETARDO_ARRANQUE_MS=15000 $PREFIJO-d03-sinmargen >/dev/null
esperar_hasta 20 sh -c "docker inspect -f '{{.State.Health.Status}}' $P-c | grep -q unhealthy"
igual "sin start-period el contenedor se marca unhealthy mientras arranca" unhealthy "$(docker inspect -f '{{.State.Health.Status}}' $P-c)"

titulo "Paso 6: --read-only"
docker run -d --name $P-ro --read-only -p 50032:8080 $V1 >/dev/null
esperar_hasta 20 sh -c "docker logs $P-ro 2>&1 | grep -q 'ERROR no puedo escribir'"
nota "sin volumen: $(docker logs $P-ro 2>&1 | grep -m1 ERROR | cut -c32-160)"
igual "sin volumen /salud/listo responde 503" 503 "$(codigo_http localhost:50032/salud/listo)"
docker rm -f $P-ro >/dev/null
docker run -d --name $P-ro --read-only --tmpfs /tmp -v $P-datos:/datos -p 50032:8080 $V1 >/dev/null
esperar_hasta 30 curl -sf localhost:50032/salud/listo
igual "con --read-only, volumen en /datos y tmpfs en /tmp: POST /api/notas" 201 "$(codigo_http -X POST --data 'solo lectura' localhost:50032/api/notas)"
negar "la raíz sigue siendo de solo lectura" docker exec $P-ro touch /app/x
nota "dueño del volumen nuevo: $(docker exec $P-ro stat -c '%u:%g' /datos) (Docker copia el dueño de /datos de la imagen al volumen vacío)"

titulo "Paso 7: memoria de 64 MB"
docker run -d --name $P-mem --memory 64m $V01 >/dev/null
sleep 6
e01="$(docker inspect -f '{{.State.Status}} oom={{.State.OOMKilled}}' $P-mem)"
heap01="$(docker run --rm --memory 64m --entrypoint java $V01 -XX:+PrintFlagsFinal -version 2>/dev/null | awk '/ MaxHeapSize /{print $4}')"
nota "0.1.0 con --memory 64m y opciones por defecto: $e01; MaxHeapSize=$heap01 (la mitad del límite: la JVM 21 ya lee el cgroup y, con tan poca memoria, aplica MinRAMPercentage=50)"
docker run -d --name $P-mem2 --memory 64m -p 50033:8080 $V1 >/dev/null
esperar_hasta 30 curl -sf localhost:50033/salud/listo
for n in $(seq 1 200); do curl -s -o /dev/null localhost:50033/api/info; done
igual "1.0.0 con 64 MB atiende 200 peticiones y sigue en marcha" "running false" "$(docker inspect -f '{{.State.Status}} {{.State.OOMKilled}}' $P-mem2)"
heap1="$(docker run --rm --memory 64m --entrypoint java $V1 -XX:MaxRAMPercentage=75 -XX:+PrintFlagsFinal -version 2>/dev/null | awk '/ MaxHeapSize /{print $4}')"
uso="$(docker stats --no-stream --format '{{.MemUsage}}' $P-mem2)"
nota "1.0.0: MaxHeapSize=$heap1 (MaxRAMPercentage solo cuenta por encima de unos 256 MB); uso real $uso"
echo "memoria64: v01='$e01' heap=$heap01 | v1 heap=$heap1 uso='$uso'" >> "$OUT"

titulo "Paso 8: la variante JRE (Ubuntu) también cumple todo"
docker run -d --name $P-jre --read-only --tmpfs /tmp --memory 64m -e RETARDO_ARRANQUE_MS=3000 -v $P-datos:/datos -p 50034:8080 $JRE >/dev/null
esperar_hasta 40 sh -c "docker inspect -f '{{.State.Health.Status}}' $P-jre | grep -qx healthy"
igual "JRE: healthy con --read-only y 64 MB" healthy "$(docker inspect -f '{{.State.Health.Status}}' $P-jre)"
igual "JRE: POST /api/notas" 201 "$(codigo_http -X POST --data 'desde jre' localhost:50034/api/notas)"

titulo "Criterio: pesa menos que la de la sesión 02"
afirmar "1.0.0 ($(mb $V1) MB) < 0.1.0 ($(mb $V01) MB)" test "$(mb $V1)" -lt "$(mb $V01)"

limpiar
docker rmi -f $PREFIJO-d03-r $PREFIJO-d03-sinmargen >/dev/null 2>&1
rm -rf "$TMP" "$MONTAJE"
terminar "Docker 03"
