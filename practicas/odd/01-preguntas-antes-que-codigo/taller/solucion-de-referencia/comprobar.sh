#!/usr/bin/env bash
# Solución de referencia del taller ODD 01: comprueba los criterios (puertos 48100-48101). Necesita JDK 21, curl, jq y Docker.
set -euo pipefail
aqui=$(cd "$(dirname "$0")" && pwd)
trabajo=$(mktemp -d)
pid=""
limpiar() {
  [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null || true
  (cd "$aqui" && docker compose -p pub-practicas-odd01 -f compose-web.yaml down -v >/dev/null 2>&1) || true
  rm -rf "$trabajo"
}
trap limpiar EXIT
fallos=0
ok() { echo "  ok     $*"; }
falla() { echo "  FALLA  $*"; fallos=$((fallos + 1)); }

# (Que las preguntas se escribieron antes que el código lo muestra el historial de tu propio repositorio; aquí no se comprueba.)
[ -s "$aqui/PREGUNTAS.md" ] && ok "PREGUNTAS.md existe" || falla "falta PREGUNTAS.md"

javac -d "$trabajo/clases" "$aqui/Servidor.java"
CLAVE="odd1-$RANDOM-$RANDOM"
(cd "$trabajo"; PUERTO=48100 DIRECTORIO_DATOS="$trabajo/datos" CLAVE_API="$CLAVE" VERSION=1.2.3 DEFECTO_NOTA_LARGA_MS=2000 \
  java -cp "$trabajo/clases" Servidor >"$trabajo/salida.jsonl" 2>&1 &
  echo $! >"$trabajo/pid")
pid=$(cat "$trabajo/pid")
for _ in $(seq 1 100); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:48100/salud/listo)" = 200 ] && break; sleep 0.2; done

echo "== Pasos 4 a 6: tráfico variado"
U=http://localhost:48100
nota_larga=$(printf 'x%.0s' $(seq 1 450))
NOTA_SECRETA="contenido-sensible-$RANDOM"
for i in 1 2 3 4 5 6; do curl -s -o /dev/null -X POST -H "X-Clave: $CLAVE" -d "nota corta $i" $U/api/notas; done
curl -s -o /dev/null -X POST -H "X-Clave: $CLAVE" -d "$NOTA_SECRETA" $U/api/notas
for i in 1 2 3; do curl -s -o /dev/null -X POST -H "X-Clave: $CLAVE" -d "$nota_larga" $U/api/notas; done
curl -s -o /dev/null -X POST -H "X-Clave: equivocada" -d "sin permiso" $U/api/notas
curl -s -o /dev/null -X POST -H "X-Clave: $CLAVE" -d "   " $U/api/notas
curl -s -o /dev/null -X PUT $U/api/notas
curl -s -o /dev/null $U/api/nada
for i in 1 2 3 4; do curl -s -o /dev/null $U/api/notas; curl -s -o /dev/null $U/api/info; done
curl -s -o /dev/null "$U/api/carga?ms=300"

# Cada línea es un JSON válido.
total=$(wc -l <"$trabajo/salida.jsonl" | tr -d " ")
validas=$(jq -c . <"$trabajo/salida.jsonl" 2>/dev/null | wc -l | tr -d " ")
[ "$total" = "$validas" ] && ok "las $total líneas de la salida son JSON válido" || falla "hay líneas que no son JSON ($validas de $total)"
P="$trabajo/peticiones.jsonl"; N="$trabajo/notas.jsonl"
jq -c 'select(.evento=="peticion")' "$trabajo/salida.jsonl" >"$P"
jq -c 'select(.evento=="nota.guardada")' "$trabajo/salida.jsonl" >"$N"
for campo in ts evento correlacion metodo ruta estado resultado duracion_ms host version servicio; do
  jq -e --arg c "$campo" 'has($c)' "$P" >/dev/null || falla "falta el campo $campo en algún evento de petición"
done
ok "todos los eventos de petición traen: ts, correlacion, metodo, ruta, estado, resultado, duracion_ms, host, version, servicio"

echo "== Paso 6: los tres resultados"
jq -rs 'group_by(.resultado)|map({resultado:.[0].resultado, n:length, estados:(map(.estado)|unique)})|.[]|"  \(.resultado): \(.n) peticiones, estados \(.estados)"' "$P"
echo "  un 503 por no estar listo cae en: $(printf '{"estado":503,"resultado":"error_servidor","motivo":"no_listo"}' | jq -r '.resultado + " con motivo " + .motivo') (se comprobará con una copia que arranca lenta, abajo)"

echo "== Paso 7: lo que NO se registra"
if grep -q "$NOTA_SECRETA" "$trabajo/salida.jsonl"; then falla "el contenido de una nota aparece en el registro"; else ok "el contenido de las notas no aparece (solo nota_longitud)"; fi
if grep -q "$CLAVE" "$trabajo/salida.jsonl" || grep -q 'equivocada' "$trabajo/salida.jsonl"; then falla "una clave aparece en el registro"; else ok "ninguna clave (ni la buena ni la equivocada) aparece en el registro"; fi

echo "== Paso 8: las diez preguntas, con la salida"
q() { echo "  P$1 $2"; shift 2; "$@" | sed 's/^/        /'; }
q 1 "peticiones 5xx / total" jq -rs '(map(select(.resultado=="error_servidor"))|length) as $e | length as $t | "\($e) de \($t)"' "$P"
q 2 "rechazos del cliente por motivo" jq -rs 'map(select(.resultado=="rechazo_cliente"))|group_by(.motivo)|map("\(.[0].motivo): \(length)")|.[]' "$P"
q 3 "p95 de duración por método y ruta (nearest-rank)" jq -rs 'group_by(.metodo+" "+.ruta)|map({k:(.[0].metodo+" "+.[0].ruta), d:(map(.duracion_ms)|sort)})|map("\(.k): p95=\(.d[((.d|length)*0.95|ceil)-1]) ms (n=\(.d|length))")|.[]' "$P"
q 4 "notas guardadas por hora" jq -rs 'group_by(.ts[0:13])|map("\(.[0].ts[0:13]):00 -> \(length)")|.[]' "$N"
q 5 "longitud de las notas guardadas (media y máximo)" jq -rs '"media \(map(.longitud)|add/length|floor), máximo \(map(.longitud)|max)"' "$N"
q 6 "notas rechazadas por inválidas" jq -rs 'map(select(.motivo=="nota_invalida"))|length|"\(.)"' "$P"
q 7 "la petición más lenta, con su correlación (por qué tardó: ver P9)" jq -rs 'sort_by(-.duracion_ms)|.[0]|"\(.correlacion) \(.metodo) \(.ruta) \(.duracion_ms) ms nota_longitud=\(.nota_longitud // "-")"' "$P"
q 8 "los 503: ¿no listo o fallo? (aquí no hubo ninguno)" jq -rs 'map(select(.estado==503))|if length==0 then "ninguno" else map(.motivo)|group_by(.)|map("\(.[0]): \(length)")|.[] end' "$P"
q 9 "qué tienen en común las peticiones lentas (>1000 ms)" jq -rs 'map(select(.duracion_ms>1000))|map("\(.metodo) \(.ruta) nota_longitud=\(.nota_longitud)")|group_by(.)|map("\(length) x \(.[0])")|.[]' "$P"
lentas_largas=$(jq -s 'map(select(.duracion_ms>1000 and .nota_longitud>400))|length' "$P")
lentas_otras=$(jq -s 'map(select(.duracion_ms>1000 and ((.nota_longitud//0)<=400)))|length' "$P")
[ "$lentas_largas" = 3 ] && [ "$lentas_otras" = 0 ] && ok "las tres notas largas son las únicas lentas: el defecto se encuentra solo con la salida" || falla "no se aísla el defecto (largas=$lentas_largas, otras=$lentas_otras)"

echo "== Pasos 5 y 9: correlación y evento de negocio"
sin=$(curl -s -D - -o /dev/null $U/api/info | tr -d '\r' | awk -F': ' 'tolower($1)=="x-correlacion"{print $2}')
con=$(curl -s -D - -o /dev/null -H 'X-Correlacion: abc-123' $U/api/info | tr -d '\r' | awk -F': ' 'tolower($1)=="x-correlacion"{print $2}')
[ -n "$sin" ] && ok "sin cabecera, la API genera uno y lo devuelve: $sin" || falla "no devolvió X-Correlacion"
[ "$con" = "abc-123" ] && ok "con cabecera, la respeta y la devuelve: $con" || falla "no respetó X-Correlacion (devolvió '$con')"
curl -s -o /dev/null -H 'X-Correlacion: con espacios y <script>' $U/api/info
sleep 0.3
jq -e -s 'map(select(.correlacion=="abc-123"))|length>=1' "$trabajo/salida.jsonl" >/dev/null && ok "el identificador recibido aparece en el evento de la petición" || falla "no aparece abc-123 en el registro"
if jq -rs 'map(select((.correlacion // "")|test("script")))|length' "$trabajo/salida.jsonl" | grep -qv '^0$'; then falla "un identificador con basura se copió al registro"; else ok "un identificador con espacios o etiquetas se descarta y se genera otro"; fi
n=$(jq -s 'length' "$N"); [ "$n" = 10 ] && ok "hay 10 eventos nota.guardada, uno por nota (campos: correlacion, longitud, notas_total)" || falla "eventos nota.guardada: $n (se esperaban 10)"
jq -e -s 'map(.notas_total)|. == (sort)' "$N" >/dev/null && ok "notas_total crece con cada nota (una pregunta que el evento de petición no responde: cuántas notas hay en el sistema)" || falla "notas_total no crece"

echo "== Paso 5: nginx genera el identificador"
(cd "$aqui" && docker compose -p pub-practicas-odd01 -f compose-web.yaml up -d --wait >/dev/null 2>&1) || falla "no arrancó nginx"
cab=$(curl -s -D - -o /dev/null localhost:48101/api/info | tr -d '\r' | awk -F': ' 'tolower($1)=="x-correlacion"{print $2}')
sleep 0.3
docker logs pub-practicas-odd01-web-1 2>&1 | grep '"evento":"nginx"' | tail -1 | sed 's/^/        nginx: /'
if [ -n "$cab" ] && jq -e -s --arg c "$cab" 'map(select(.correlacion==$c))|length==1' "$trabajo/salida.jsonl" >/dev/null; then
  ok "la petición por nginx volvió con X-Correlacion=$cab y la API la registró con ese mismo identificador"
else falla "la correlación de nginx no llegó a la API"; fi
cab2=$(curl -s -D - -o /dev/null -H 'X-Correlacion: mi-id-1' localhost:48101/api/info | tr -d '\r' | awk -F': ' 'tolower($1)=="x-correlacion"{print $2}')
[ "$cab2" = "mi-id-1" ] && ok "nginx respeta el identificador que trae el cliente" || falla "nginx no respetó el del cliente ($cab2)"

echo "== Paso 6: un 503 por no estar listo"
kill "$pid"; sleep 1; kill -9 "$pid" 2>/dev/null || true
(cd "$trabajo"; PUERTO=48100 DIRECTORIO_DATOS="$trabajo/datos2" RETARDO_ARRANQUE_MS=3000 java -cp "$trabajo/clases" Servidor >"$trabajo/lenta.jsonl" 2>&1 &
  echo $! >"$trabajo/pid")
pid=$(cat "$trabajo/pid"); sleep 1
curl -s -o /dev/null $U/api/info; curl -s -o /dev/null $U/salud/listo
jq -c 'select(.evento=="peticion" and .estado==503)|{ruta,estado,resultado,motivo}' "$trabajo/lenta.jsonl" | sed 's/^/        /'
[ "$(jq -s 'map(select(.estado==503 and .motivo=="no_listo" and .resultado=="error_servidor"))|length' <(jq -c 'select(.evento=="peticion")' "$trabajo/lenta.jsonl"))" = 2 ] \
  && ok "un 503 por no estar listo cae en error_servidor, y motivo=no_listo lo distingue de un fallo real" || falla "los 503 no quedaron clasificados"
[ "$fallos" -eq 0 ]
