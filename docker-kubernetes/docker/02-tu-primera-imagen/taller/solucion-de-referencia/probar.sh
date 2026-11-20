#!/usr/bin/env bash
# Solución ejecutable del taller Docker 02. Construye las imágenes de esta carpeta y mide lo que pide el enunciado.
# Deja construidas ${PREFIJO}-bitacora-web:0.1.0 y ${PREFIJO}-bitacora-api:0.1.0 (se borran al terminar, salvo con MANTENER=1).
# Puertos del equipo: 50021 y 50022.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
MONTAJE="$(mktemp -d "${TMPDIR:-/tmp}/$PREFIJO-montaje.XXXXXX")"; montar_solucion "$MONTAJE"   # esta carpeta + el código de docker-kubernetes/app
SOL="$MONTAJE"
P="$PREFIJO-d02"
WEB=$PREFIJO-bitacora-web:0.1.0
API=$PREFIJO-bitacora-api:0.1.0
TMP="$(mktemp -d "${TMPDIR:-/tmp}/$PREFIJO-d02.XXXXXX")"
OUT="$SALIDAS/docker-02.txt"; : > "$OUT"
limpiar() { docker rm -fv $P-web $P-web2 $P-api $P-shell >/dev/null 2>&1; }
limpiar

construir() { # construir etiqueta carpeta -> imprime los milisegundos
  local i; i=$(ahora_ms)
  docker build --progress=plain -t "$1" "$2" > "$TMP/build.log" 2>&1 || { cat "$TMP/build.log"; return 1; }
  echo $(( $(ahora_ms) - i ))
}

titulo "Docker 02, paso 1: imagen web"
afirmar "Dockerfile de la web: ninguna base usa latest" sh -c "! grep -E '^FROM .*(:latest|^FROM [^:]+$)' '$SOL/web/Dockerfile'"
construir $WEB "$SOL/web" >/dev/null && ok "bitacora-web:0.1.0 construida con una orden" || mal "no construye la web"
docker run -d --name $P-web -p 50021:8080 -e DESTINO_API=http://127.0.0.1:9 $WEB >/dev/null
esperar_hasta 20 curl -sf localhost:50021/
afirmar "la página carga aunque la API no exista (DESTINO_API con IP)" sh -c "curl -s localhost:50021/ | grep -q '<title>Bitácora</title>'"
igual "la ruta /api/ devuelve 502 porque no hay nadie en 127.0.0.1:9" 502 "$(codigo_http localhost:50021/api/info)"
docker exec $P-web cat /etc/nginx/conf.d/default.conf | grep proxy_pass | sed 's/^/   generado: /'
docker run -d --name $P-web2 -e DESTINO_API=http://no-existe-este-host:8080 $WEB >/dev/null
sleep 3
estado="$(docker inspect -f '{{.State.Status}} {{.State.ExitCode}}' $P-web2)"
igual "con un nombre de host que no existe nginx no arranca" "exited 1" "$estado"
linea="$(docker logs $P-web2 2>&1 | grep -m1 'host not found')"
nota "registro: $linea"
echo "web_host_inexistente: $linea" >> "$OUT"

titulo "Paso 2 y 3: API de una etapa, arranque y rutas"
t=$(construir $API "$SOL/api") && ok "bitacora-api:0.1.0 construida desde cero en ${t} ms (con la base ya descargada)" || mal "no construye la API"
docker run -d --name $P-api -p 50022:8080 $API >/dev/null
esperar_hasta 30 curl -sf localhost:50022/salud/listo
igual "/salud/listo" 200 "$(codigo_http localhost:50022/salud/listo)"
igual "/api/info" 200 "$(codigo_http localhost:50022/api/info)"
nota "$(curl -s localhost:50022/api/info)"

titulo "Paso 4: tamaño y capa más pesada"
tam="$(docker image inspect -f '{{.Size}}' $API)"
nota "tamaño de $API: $((tam / 1000000)) MB"
docker history --format '{{.Size}}\t{{.CreatedBy}}' $API | head -8 | cut -c1-110 | sed 's/^/   /'
echo "api_0.1.0_bytes=$tam" >> "$OUT"

titulo "Paso 5: caché"
cp -R "$SOL/api" "$TMP/api"
t_sin=$(construir $PREFIJO-d02-cache "$TMP/api"); cach_sin=$(grep -c ' CACHED' "$TMP/build.log")
# Cambio de código único en cada ejecución, para que no lo encuentre en la caché de una ejecución anterior.
echo "// cambio $(date +%s)$RANDOM" >> "$TMP/api/Servidor.java"
t_cod=$(construir $PREFIJO-d02-cache "$TMP/api"); cach_cod=$(grep -c ' CACHED' "$TMP/build.log")
grep -E '^#[0-9]+ \[[0-9]/[0-9]\]' "$TMP/build.log" | sort -u | sed 's/^/   /'
printf '\n# comentario al final\n' >> "$TMP/api/Dockerfile"
t_com=$(construir $PREFIJO-d02-cache "$TMP/api"); cach_com=$(grep -c ' CACHED' "$TMP/build.log")
nota "sin cambios: ${t_sin} ms, $cach_sin pasos en caché | cambio en Servidor.java: ${t_cod} ms, $cach_cod en caché | comentario en el Dockerfile: ${t_com} ms, $cach_com en caché"
igual "con el código cambiado se reutilizan solo FROM y WORKDIR" 1 "$cach_cod"
igual "un comentario no invalida ninguna capa" 3 "$cach_com"
echo "cache: sin_cambios=${t_sin}ms/$cach_sin codigo=${t_cod}ms/$cach_cod comentario=${t_com}ms/$cach_com" >> "$OUT"

titulo "Paso 6: basura.bin de 200 MB"
mkdir -p "$TMP/ingenua"
cp "$SOL/api/Servidor.java" "$TMP/ingenua/"
cat > "$TMP/ingenua/Dockerfile" <<'EOF'
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN javac -d clases Servidor.java
ENTRYPOINT ["java", "-cp", "clases", "Servidor"]
EOF
dd if=/dev/zero of="$TMP/ingenua/basura.bin" bs=1048576 count=200 2>/dev/null
t_ing=$(construir $PREFIJO-d02-ingenua "$TMP/ingenua")
ctx_ing="$(grep -o 'transferring context: [0-9.]*[kKMG]*B' "$TMP/build.log" | tail -1)"
tam_ing="$(docker image inspect -f '{{.Size}}' $PREFIJO-d02-ingenua)"
dd if=/dev/zero of="$TMP/api/basura.bin" bs=1048576 count=200 2>/dev/null
cp "$SOL/api/Dockerfile" "$TMP/api/Dockerfile"; cp "$SOL/api/Servidor.java" "$TMP/api/"
t_sol=$(construir $PREFIJO-d02-cache "$TMP/api")
ctx_sol="$(grep -o 'transferring context: [0-9.]*[kKMG]*B' "$TMP/build.log" | tail -1)"
tam_sol="$(docker image inspect -f '{{.Size}}' $PREFIJO-d02-cache)"
nota "COPY . . sin .dockerignore: ${t_ing} ms, $ctx_ing, imagen $((tam_ing / 1000000)) MB"
nota "solución (.dockerignore en lista blanca): ${t_sol} ms, $ctx_sol, imagen $((tam_sol / 1000000)) MB"
afirmar "basura.bin está en la imagen ingenua" docker run --rm --entrypoint ls $PREFIJO-d02-ingenua /app/basura.bin
negar "basura.bin no está en la imagen de la solución" docker run --rm --entrypoint ls $PREFIJO-d02-cache /app/basura.bin
afirmar "el contexto de la solución no lleva los 200 MB" sh -c "echo '$ctx_sol' | grep -Eq '[0-9.]+(B|kB|KB)$'"
echo "basura: ingenua=${t_ing}ms '$ctx_ing' ${tam_ing}B solucion=${t_sol}ms '$ctx_sol' ${tam_sol}B" >> "$OUT"

titulo "Paso 7: tiempo de parada"
i=$(ahora_ms); docker stop -t 10 $P-api >/dev/null; t_stop=$(( $(ahora_ms) - i ))
nota "docker stop -t 10: ${t_stop} ms, código $(docker inspect -f '{{.State.ExitCode}}' $P-api)"
afirmar "la API se detiene en menos de 4 s" test "$t_stop" -lt 4000
docker logs $P-api 2>&1 | tail -2 | sed 's/^/   /'
cat > "$TMP/shell.Dockerfile" <<'EOF'
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY Servidor.java .
RUN javac -d clases Servidor.java
CMD java -cp clases Servidor
EOF
docker build -q -f "$TMP/shell.Dockerfile" -t $PREFIJO-d02-shell "$SOL/api" >/dev/null
docker run -d --name $P-shell $PREFIJO-d02-shell >/dev/null; sleep 3
pid1="$(docker exec $P-shell sh -c 'tr "\0" " " < /proc/1/cmdline')"
i=$(ahora_ms); docker stop -t 10 $P-shell >/dev/null; t_shell=$(( $(ahora_ms) - i ))
nota "forma shell: PID 1 = '$pid1'; docker stop -t 10 tardó ${t_shell} ms, código $(docker inspect -f '{{.State.ExitCode}}' $P-shell)"
afirmar "con la forma shell la señal no llega a java y Docker mata a los 10 s" test "$t_shell" -ge 9500
docker rm -f $P-shell >/dev/null
echo "parada: exec=${t_stop}ms shell=${t_shell}ms pid1_shell='$pid1'" >> "$OUT"

titulo "Paso 8: notas en un contenedor recreado"
docker rm -f $P-api >/dev/null
docker run -d --name $P-api -p 50022:8080 $API >/dev/null; esperar_hasta 30 curl -sf localhost:50022/salud/listo
igual "POST /api/notas" 201 "$(codigo_http -X POST --data 'nota del taller 02' localhost:50022/api/notas)"
nota "antes: $(curl -s localhost:50022/api/notas)"
docker rm -f $P-api >/dev/null
docker run -d --name $P-api -p 50022:8080 $API >/dev/null; esperar_hasta 30 curl -sf localhost:50022/salud/listo
despues="$(curl -s localhost:50022/api/notas)"
nota "después de recrear: $despues"
afirmar "la nota se perdió (vivía en la capa del contenedor)" sh -c "echo '$despues' | grep -q '\"notas\":\[\]'"

limpiar
docker rmi -f $PREFIJO-d02-cache $PREFIJO-d02-ingenua $PREFIJO-d02-shell >/dev/null 2>&1
rm -rf "$TMP" "$MONTAJE"
terminar "Docker 02"
