#!/usr/bin/env bash
# Solución de referencia del taller ODD 02: comprueba los criterios. Necesita JDK 21, Docker, curl, Python 3 y red para bajar el agente. Puertos 48300 (Grafana), 48317/48318 (OTLP), 48301 (nginx), 48310-48312 (API).
# Todo lo que se mira se mira a través de la interfaz de Grafana (/api/datasources/proxy), como lo haría una persona.
set -euo pipefail
aqui=$(cd "$(dirname "$0")" && pwd)
# --- Agente de OpenTelemetry y su API: se descargan una vez de Maven Central y se verifican con su suma SHA-256 -------------
# Carpeta de caché (fuera del repositorio): ${CACHE_OTEL:-${XDG_CACHE_HOME:-$HOME/.cache}/de-cero-a-senior/opentelemetry}
C="${CACHE_OTEL:-${XDG_CACHE_HOME:-$HOME/.cache}/de-cero-a-senior/opentelemetry}"
descargar_otel() {
  mkdir -p "$C"
  local base=https://repo1.maven.org/maven2/io/opentelemetry f
  for f in \
    "agente.jar|$base/javaagent/opentelemetry-javaagent/2.33.1/opentelemetry-javaagent-2.33.1.jar|e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149" \
    "otel-api.jar|$base/opentelemetry-api/1.67.0/opentelemetry-api-1.67.0.jar|538d1dff2f9a37aecbc0ef98fd17273a3affb058e734e6763a3a07a38a5fdc59" \
    "otel-context.jar|$base/opentelemetry-context/1.67.0/opentelemetry-context-1.67.0.jar|40fb813fb5e27ec2691daa29c81b572b5b1541ef9b41e62490c3ee3e6407c55e" \
    "otel-common.jar|$base/opentelemetry-common/1.67.0/opentelemetry-common-1.67.0.jar|da97bdb395418e16ecdcbe7ad3497f45b9e767ceda4ae7e86133e7a757ac377f"; do
    IFS='|' read -r nombre url suma <<<"$f"
    if [ ! -f "$C/$nombre" ] || [ "$(shasum -a 256 "$C/$nombre" | awk '{print $1}')" != "$suma" ]; then
      curl -sfL -o "$C/$nombre.parte" "$url" || { echo "No pude descargar $url" >&2; exit 2; }
      [ "$(shasum -a 256 "$C/$nombre.parte" | awk '{print $1}')" = "$suma" ] || { echo "La suma de $nombre no coincide" >&2; rm -f "$C/$nombre.parte"; exit 2; }
      mv "$C/$nombre.parte" "$C/$nombre"
    fi
  done
}
descargar_otel
# --------------------------------------------------------------------------------------------------------------------------
VERSION_LGTM=${VERSION_LGTM:-0.40.0}
export VERSION_LGTM
proyecto=pub-practicas-odd02
trabajo=$(mktemp -d)
pids=()
compose() { (cd "$aqui" && docker compose -p "$proyecto" "$@"); }
limpiar() {
  for p in "${pids[@]:-}"; do [ -n "$p" ] && kill -9 "$p" 2>/dev/null || true; done
  compose --profile web down -v >/dev/null 2>&1 || true
  rm -rf "$trabajo"
}
trap limpiar EXIT
fallos=0
ok() { echo "  ok     $*"; }
falla() { echo "  FALLA  $*"; fallos=$((fallos + 1)); }
G=localhost:48300/api/datasources/proxy/uid
prom() { curl -s -m 20 -u admin:admin --data-urlencode "query=$1" "$G/prometheus/api/v1/query"; }
# Tempo tarda en poder buscar lo recién llegado: se reintenta hasta que haya resultados o se agote el tiempo.
valor() {
  local v n
  for ((n = 0; n < ${REINTENTOS:-30}; n++)); do
    v=$(prom "$1" | python3 -c "import sys,json;r=json.load(sys.stdin)['data']['result'];print(r[0]['value'][1] if r else 'NaN')" 2>/dev/null || echo NaN)
    [ "$v" != NaN ] && break
    sleep 3
  done
  echo "$v"
}
tempo_q() {
  local q=$1 limite=${2:-20} minimo=${3:-1} n r
  for ((n = 0; n < 60; n++)); do
    r=$(curl -s -m 20 -u admin:admin -G --data-urlencode "q=$q" --data-urlencode "limit=$limite" "$G/tempo/api/search")
    if python3 -c "import sys,json;sys.exit(0 if len(json.loads(sys.argv[1]).get('traces',[]))>=int(sys.argv[2]) else 1)" "$r" "$minimo" 2>/dev/null; then echo "$r"; return 0; fi
    sleep 3
  done
  echo "$r"
}
lanzar_api() {  # lanzar_api <puerto> <servicio> [VAR=valor...]
  local puerto=$1 servicio=$2; shift 2
  mkdir -p "$trabajo/$puerto"
  (cd "$trabajo/$puerto"; env OTEL_SERVICE_NAME="$servicio" OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:48318 OTEL_METRIC_EXPORT_INTERVAL=5000 OTEL_BSP_SCHEDULE_DELAY=1000 \
     OTEL_LOGS_EXPORTER=none PUERTO="$puerto" DIRECTORIO_DATOS="$trabajo/$puerto/datos" "$@" \
     java -javaagent:"$C/agente.jar" -cp "$trabajo/clases:$CPA" Servidor >"$trabajo/$puerto/salida.jsonl" 2>&1 </dev/null &
   echo $! >"$trabajo/$puerto/pid")
  pids+=("$(cat "$trabajo/$puerto/pid")")
  for _ in $(seq 1 100); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:$puerto/salud/listo)" = 200 ] && return 0; sleep 0.2; done
  echo "la API $puerto no arrancó" >&2; return 1
}
CPA="$C/otel-api.jar:$C/otel-context.jar:$C/otel-common.jar"
javac -cp "$CPA" -d "$trabajo/clases" "$aqui/Servidor.java"

echo "== Paso 1: el entorno (grafana/otel-lgtm:$VERSION_LGTM)"
compose --profile web up -d --wait >/dev/null 2>&1 || { echo "no levantó el entorno"; exit 1; }
# Grafana sana no significa que Tempo y el recolector ya reciban: sin esta espera, las primeras trazas se pierden.
for _ in $(seq 1 90); do
  [ "$(curl -s -m 3 -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' localhost:48318/v1/traces)" = 200 ] \
    && [ "$(curl -s -m 3 -u admin:admin "$G/tempo/ready")" = ready ] && break
  sleep 2
done
curl -s -m 5 -u admin:admin localhost:48300/api/health | python3 -c "import sys,json;d=json.load(sys.stdin);print('  Grafana', d['version'], 'database', d['database'])"
ok "la interfaz responde en :48300 y los orígenes de datos son: $(curl -s -u admin:admin localhost:48300/api/datasources | python3 -c "import sys,json;print(', '.join(d['name'] for d in json.load(sys.stdin)))")"

echo "== Paso 2: el agente (OpenTelemetry Java agent $(unzip -p "$C/agente.jar" META-INF/MANIFEST.MF | awk -F': ' '/Implementation-Version/{print $2}' | tr -d '\r'))"
lanzar_api 48310 bitacora-api
sed -n '2p' "$trabajo/48310/salida.jsonl" | cut -c1-140 | sed 's/^/        /'
CL=zzz
for i in 1 2 3 4 5; do curl -s -o /dev/null -X POST -d "nota $i" localhost:48310/api/notas; curl -s -o /dev/null localhost:48310/api/notas; done
for i in $(seq 1 12); do  # el agente exporta por lotes: se espera hasta 60 s a que Tempo las indexe, en vez de un sleep fijo
  sleep 5
  tipos=$(tempo_q '{ resource.service.name = "bitacora-api" }' 50 | python3 -c "import sys,json;print(sorted({t['rootTraceName'] for t in json.load(sys.stdin)['traces']}))")
  [[ "$tipos" == *"POST /api/notas"* ]] && break
done
echo "        trazas raíz del servicio: $tipos"
if [[ "$tipos" == *"POST /api/notas"* ]]; then ok "el agente 2.x instrumenta el servidor HTTP del JDK (com.sun.net.httpserver): hay trazas sin tocar el código"; else falla "no hay trazas del servidor HTTP"; fi

echo "== Pasos 3 y 4: tramos manuales y una traza que cruza nginx y la API"
DESTINO_API=http://host.docker.internal:48310 compose --profile web up -d --force-recreate web >/dev/null 2>&1
sleep 3
curl -s -o /dev/null -X POST -d "por nginx" localhost:48301/api/notas
curl -s -o /dev/null localhost:48301/api/notas
sleep 15
T=$(tempo_q '{ resource.service.name = "nginx-web" }' 5 | python3 -c "import sys,json;print(json.load(sys.stdin)['traces'][0]['traceID'])")
curl -s -u admin:admin "$G/tempo/api/traces/$T" | python3 -c "
import sys,json
d=json.load(sys.stdin); filas=[]
for b in d['batches']:
    svc=[a['value']['stringValue'] for a in b['resource']['attributes'] if a['key']=='service.name'][0]
    for ss in b['scopeSpans']:
        for s in ss['spans']: filas.append((svc, s['name'], s['spanId'], s.get('parentSpanId',''), {a['key']:list(a['value'].values())[0] for a in s.get('attributes',[])}))
servicios={f[0] for f in filas}
print('        traza', '$T', 'servicios', sorted(servicios))
for f in filas: print('          ', f[0], '|', f[1], '|', {k:v for k,v in f[4].items() if k.startswith('bitacora.') and k!='bitacora.correlacion'})
ids={f[2]:f for f in filas}
cruza = servicios=={'nginx-web','bitacora-api'} and any(f[0]=='bitacora-api' and f[3] in ids and ids[f[3]][0]=='nginx-web' for f in filas)
sys.exit(0 if cruza else 1)" && ok "una sola traza cruza nginx y la API: el tramo del servidor de la API es hijo del de nginx (nginx:stable-alpine-otel, módulo ngx_otel_module)" || falla "la traza se parte entre nginx y la API"
conhijos=$(curl -s -u admin:admin "$G/tempo/api/traces/$T" | python3 -c "
import sys,json
d=json.load(sys.stdin)
n=[s['name'] for b in d['batches'] for ss in b['scopeSpans'] for s in ss['spans']]
print(' '.join(sorted(n)))")
echo "        tramos de la traza de la escritura por nginx (la última búsqueda fue una lectura): $conhijos"
E=$(tempo_q '{ name = "notas.escribir" }' 3 | python3 -c "import sys,json;print(json.load(sys.stdin)['traces'][0]['traceID'])")
curl -s -u admin:admin "$G/tempo/api/traces/$E" | python3 -c "
import sys,json
d=json.load(sys.stdin)
for b in d['batches']:
  for ss in b['scopeSpans']:
    for s in ss['spans']:
      if s['name']=='notas.escribir':
        a={x['key']:list(x['value'].values())[0] for x in s['attributes']}
        print('        notas.escribir:', {k:v for k,v in a.items() if k.startswith('bitacora.')})
        assert 'bitacora.nota.longitud' in a and not any('texto' in k or 'contenido' in k for k in a)" && ok "el tramo de escritura trae longitud y secuencia, y ningún atributo con el contenido de la nota" || falla "atributos del tramo"

echo "== Paso 5: del registro a la traza"
linea=$(grep '"evento":"peticion"' "$trabajo/48310/salida.jsonl" | grep '"metodo":"POST"' | tail -1)
tid=$(python3 -c "import sys,json;print(json.loads(sys.argv[1])['trace_id'])" "$linea")
n=$(curl -s -u admin:admin "$G/tempo/api/traces/$tid" | python3 -c "import sys,json;d=json.load(sys.stdin);print(sum(len(ss['spans']) for b in d['batches'] for ss in b['scopeSpans']))")
[ "$n" -ge 2 ] && ok "el trace_id $tid del evento del registro abre una traza con $n tramos en Tempo" || falla "el trace_id del registro no existe en Tempo"

echo "== Paso 6: métricas (tasa, errores, duración por ruta, contador de negocio)"
curl -s -o /dev/null -X PUT localhost:48310/api/notas; curl -s -o /dev/null localhost:48310/api/nada
curl -s -o /dev/null -X POST -H 'X-Clave: x' -d '   ' localhost:48310/api/notas
sleep 8
echo "        rutas con métricas: $(prom 'count by (ruta) (bitacora_peticiones_total)' | python3 -c "import sys,json;print(sorted(r['metric']['ruta'] for r in json.load(sys.stdin)['data']['result']))")"
echo "        peticiones por clase de estado: $(prom 'sum by (clase_estado) (bitacora_peticiones_total)' | python3 -c "import sys,json;print({r['metric']['clase_estado']:r['value'][1] for r in json.load(sys.stdin)['data']['result']})")"
[ "$(valor 'sum(bitacora_notas_guardadas_total{resultado="ok"})')" != NaN ] && ok "contador de negocio: $(valor 'sum(bitacora_notas_guardadas_total{resultado="ok"})') notas guardadas" || falla "no hay contador de notas"
[ "$(valor 'sum(bitacora_duracion_milliseconds_count{ruta="/api/notas"})')" != NaN ] && ok "histograma de duración por ruta presente" || falla "no hay histograma"
[ "$(valor 'sum(bitacora_peticiones_total{clase_estado="4xx"})')" != NaN ] && ok "los rechazos aparecen como 4xx, separados de los errores del servidor" || falla "no hay 4xx"

echo "== Paso 7: una etiqueta de alta cardinalidad a propósito"
lanzar_api 48311 bitacora-api-alta ETIQUETA_ALTA_CARDINALIDAD=1
for i in $(seq 1 150); do curl -s -o /dev/null -X POST -d "texto único $i $RANDOM" localhost:48311/api/notas; done
for i in $(seq 1 150); do curl -s -o /dev/null -X POST -d "texto único $i $RANDOM" localhost:48310/api/notas; done
sleep 10
s_alta=$(valor 'count(bitacora_notas_guardadas_total{service_name="bitacora-api-alta"})')
s_sana=$(valor 'count(bitacora_notas_guardadas_total{service_name="bitacora-api"})')
echo "        series de bitacora_notas_guardadas_total: con el texto como etiqueta = $s_alta; sin ella = $s_sana"
[ "${s_alta%.*}" -ge 150 ] && [ "${s_sana%.*}" -le 2 ] && ok "150 notas distintas crearon $s_alta series con la etiqueta de alta cardinalidad; sin ella son $s_sana" || falla "no se observó la explosión de series"
kill -9 "$(cat "$trabajo/48311/pid")" 2>/dev/null || true

echo "== Paso 8: media frente a p95 y p99 de /api/carga"
for ms in $(for _ in $(seq 1 90); do echo 10; done) $(for _ in $(seq 1 8); do echo 300; done) 2000 2000; do curl -s -o /dev/null "localhost:48310/api/carga?ms=$ms" & [ "$ms" = 2000 ] && wait; done; wait
sleep 8
R='ruta="/api/carga"'
media=$(valor "sum(bitacora_duracion_milliseconds_sum{$R}) / sum(bitacora_duracion_milliseconds_count{$R})")
p50=$(valor "histogram_quantile(0.50, sum by (le) (bitacora_duracion_milliseconds_bucket{$R}))")
p95=$(valor "histogram_quantile(0.95, sum by (le) (bitacora_duracion_milliseconds_bucket{$R}))")
p99=$(valor "histogram_quantile(0.99, sum by (le) (bitacora_duracion_milliseconds_bucket{$R}))")
LC_ALL=C printf '        100 peticiones: 90 de 10 ms, 8 de 300 ms, 2 de 2000 ms\n        media = %.0f ms, p50 = %.0f ms, p95 = %.0f ms, p99 = %.0f ms\n' "$media" "$p50" "$p95" "$p99"
python3 -c "import sys;m,a,b,c=map(float,sys.argv[1:]);sys.exit(0 if a<m<b<=c or (m<b and b<c) else 1)" "$media" "$p50" "$p95" "$p99" && ok "la media (${media%.*} ms) esconde lo que p95 (${p95%.*} ms) y p99 (${p99%.*} ms) muestran: el 10 % de las peticiones espera 30 o 200 veces más que la típica" || falla "relación media/percentiles inesperada"

echo "== Pasos 9 y 10: SLI, SLO y presupuesto, con los datos"
buenas=$(valor 'sum(increase(bitacora_duracion_milliseconds_bucket{ruta="/api/notas",metodo="POST",clase_estado="2xx",le="500"}[1h]))')
total=$(valor 'sum(increase(bitacora_duracion_milliseconds_count{ruta="/api/notas",metodo="POST"}[1h]))')
echo "        guardar una nota, última hora: $buenas buenas de $total (SLI = $(python3 -c "print(round(float('$buenas')/float('$total')*100,2))") %)"
python3 - <<PY
dias=7; objetivo=0.99; pres=1-objetivo
print(f"        presupuesto de error del 99 % en {dias} días: {pres*100:.0f} % = {pres*dias*24*60:.1f} minutos en {dias} días,"
      f" o {pres*10000:.0f} de cada 10 000 peticiones")
print(f"        objetivo de leer notas, 99,5 % en 30 días: {0.005*30*24:.1f} horas de presupuesto, {0.005*10000:.0f} de cada 10 000 peticiones")
PY

echo "== Paso 10: el panel"
python3 - "$aqui/panel-presupuesto.json" <<'PY' > "$trabajo/dash.json"
import sys,json
print(json.dumps({"dashboard": json.load(open(sys.argv[1])), "overwrite": True, "folderId": 0}))
PY
curl -s -m 20 -u admin:admin -H 'Content-Type: application/json' -d @"$trabajo/dash.json" localhost:48300/api/dashboards/db | python3 -c "import sys,json;d=json.load(sys.stdin);print('        panel importado:', d.get('status'), d.get('url'))"
python3 - "$aqui/panel-presupuesto.json" <<'PY' > "$trabajo/exprs.txt"
import sys,json
for p in json.load(open(sys.argv[1]))['panels']:
    print(p['title'] + '\t' + p['targets'][0]['expr'])
PY
sin_datos=0
while IFS=$'\t' read -r titulo expr; do
  expr=${expr//\[1m\]/[30m]}; expr=${expr//\[5m\]/[30m]}   # el tráfico ya terminó: se mira una ventana larga
  v=$(REINTENTOS=1 valor "$expr"); echo "        [$titulo] -> $v"
  [ "$v" = NaN ] && sin_datos=$((sin_datos + 1))
done <"$trabajo/exprs.txt"
[ "$sin_datos" -eq 0 ] && ok "las seis consultas del panel devuelven datos (el panel se importó en Grafana)" || echo "        (consultas sin datos todavía: $sin_datos, p. ej. la de 5xx hasta que haya un 5xx)"

echo "== Paso 11: una de cada diez escrituras tarda dos segundos; solo con la interfaz"
lanzar_api 48312 bitacora-api-v2 DEFECTO_ESCRITURA_LENTA_CADA=10
inicio=$(date +%s)
for i in $(seq 1 40); do curl -s -o /dev/null -X POST -d "nota v2 $i" localhost:48312/api/notas & [ $((i % 5)) = 0 ] && wait; done; wait
for i in $(seq 1 10); do curl -s -o /dev/null localhost:48312/api/notas; done
sleep 10
echo "        consulta 1 (panel): p95 por ruta de bitacora-api-v2:"
prom 'histogram_quantile(0.95, sum by (le, ruta) (increase(bitacora_duracion_milliseconds_bucket{service_name="bitacora-api-v2"}[5m])))' | python3 -c "
import sys,json,math
for r in json.load(sys.stdin)['data']['result']:
    v=float(r['value'][1])
    if not math.isnan(v): print('          ', r['metric'].get('ruta'), round(v),'ms')"
echo "        consulta 2 (trazas): tramos lentos"
tempo_q '{ resource.service.name = "bitacora-api-v2" && duration > 1s }' 50 4 | python3 -c "
import sys,json
d=json.load(sys.stdin)['traces']
print('          trazas de más de 1 s:', len(d), '; operación raíz:', {t['rootTraceName'] for t in d})" | tee "$trabajo/lentas.txt"
curl -s -u admin:admin -G --data-urlencode 'q={ resource.service.name = "bitacora-api-v2" && name = "notas.escribir" && duration > 1s }' --data-urlencode "start=$(( inicio - 60 ))" --data-urlencode "end=$(( $(date +%s) + 60 ))" --data-urlencode limit=50 "$G/tempo/api/search" | python3 -c "
import sys,json
print('          trazas con notas.escribir > 1 s:', len(json.load(sys.stdin)['traces']))"
echo "        consulta 3 (un tramo concreto): la secuencia de las escrituras lentas"
secuencias=$(for T in $(tempo_q '{ resource.service.name = "bitacora-api-v2" && name = "notas.escribir" && duration > 1s }' 50 4 | python3 -c "import sys,json;print(' '.join(t['traceID'] for t in json.load(sys.stdin)['traces']))"); do
  curl -s -u admin:admin "$G/tempo/api/traces/$T" | python3 -c "
import sys,json
for b in json.load(sys.stdin)['batches']:
  for ss in b['scopeSpans']:
    for s in ss['spans']:
      if s['name']=='notas.escribir':
        a={x['key']:list(x['value'].values())[0] for x in s['attributes']}
        print(a['bitacora.escritura.secuencia'], round((int(s['endTimeUnixNano'])-int(s['startTimeUnixNano']))/1e6), s['startTimeUnixNano'])"
done | sort -n)
echo "$secuencias" | awk '{printf "          secuencia %s, %s ms\n",$1,$2}'
python3 - "$secuencias" <<'PY' && ok "las escrituras lentas son exactamente las de secuencia 10, 20, 30 y 40: operación notas.escribir, y lo común es el número de escritura múltiplo de 10 (no la longitud, que es la misma)" || falla "no se aisló el patrón"
import sys
filas=[l.split() for l in sys.argv[1].strip().splitlines()]
seq=[int(f[0]) for f in filas]
sys.exit(0 if seq==[10,20,30,40] else 1)
PY
primera=$(echo "$secuencias" | head -1 | awk '{print $3}')
echo "        desde cuándo: la primera escritura lenta empezó a las $(date -r $((primera/1000000000)) +%H:%M:%S) (las lentas aparecen desde que corre esa copia: ver 'inicio' de la prueba)"

echo "== Paso 12: la regla de alerta"
docker cp "$aqui/alerta-presupuesto.yaml" "${proyecto}-observabilidad-1:/tmp/alerta.yaml"
salida=$(docker exec "${proyecto}-observabilidad-1" /otel-lgtm/prometheus/promtool check rules /tmp/alerta.yaml 2>&1) && ok "promtool: $(echo "$salida" | tr '\n' ' ')" || { falla "promtool rechazó la regla"; echo "$salida"; }
r1=$(valor 'sum(increase(bitacora_duracion_milliseconds_bucket{service_name="bitacora-api-v2",ruta="/api/notas",metodo="POST",clase_estado="2xx",le="500"}[1h])) / sum(increase(bitacora_duracion_milliseconds_count{service_name="bitacora-api-v2",ruta="/api/notas",metodo="POST"}[1h]))')
python3 -c "
r=float('$r1'); malas=1-r; burn=malas/0.01
print(f'        con el defecto: SLI={r*100:.1f} %, malas={malas*100:.1f} %, ritmo de gasto={burn:.1f} veces el permitido (umbrales: 14,4 página, 6 ticket)')
import sys; sys.exit(0 if 6<burn<25 else 1)" && ok "con una de cada diez escrituras lenta, el ritmo de gasto (~10x) supera el umbral de 6 (ticket): la alerta lenta sí suena. Con solo 40 peticiones `increase` extrapola y el valor medido cae entre ~10 y ~20 según la ejecución, así que puede quedar a uno u otro lado del de 14,4 (página)" || falla "el ritmo de gasto no supera el umbral de 6"
[ "$fallos" -eq 0 ]
