#!/usr/bin/env bash
# Taller 07-07, puntos 2, 3 y 5: Prometheus + Grafana + Tempo en Docker, dos servicios reales,
# latencia artificial en el cobro y diagnóstico usando solo métricas y trazas.
set -euo pipefail
cd "$(dirname "$0")"
JAR=target/n7-07-observabilidad-1.0.0.jar
OUT=target/escenario; rm -rf "$OUT"; mkdir -p "$OUT"
PROM=http://localhost:42390; GRAF=http://localhost:42300; TEMPO=http://localhost:42320
PIDS=()
limpiar() {
  for p in "${PIDS[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null || true; done
  docker compose -f infra/docker-compose.yml down -v >/dev/null 2>&1 || true
}
trap limpiar EXIT

[ -f "$JAR" ] || mvn -q package -DskipTests
docker compose -f infra/docker-compose.yml up -d >/dev/null
# promtool: valida las reglas de alerta
docker run --rm --entrypoint promtool -v "$PWD/infra:/c:ro" prom/prometheus:v3.5.0 check rules /c/alertas.yml | tee "$OUT/promtool.txt"
# promtool test: cada alerta con un caso que dispara y otro que no
docker run --rm --entrypoint promtool -v "$PWD/infra:/c:ro" prom/prometheus:v3.5.0 test rules /c/alertas_test.yml | tee -a "$OUT/promtool.txt"

arrancar() { # nombre puerto destino latencia
  APP_NOMBRE=$1 OTLP_TRAZAS=http://localhost:42318/v1/traces \
    java -jar "$JAR" --server.port=$2 --app.destino=$3 --app.latencia-cobro-ms=$4 > "$OUT/$1-$2.log" 2>&1 &
  echo $!
}
esperar() { for i in $(seq 1 60); do curl -sf "http://localhost:$1/actuator/health/readiness" >/dev/null && return 0; sleep 1; done; echo "no arrancó $1"; exit 1; }
carga() { for i in $(seq 1 "$1"); do curl -s "http://localhost:42201/a/pedido/$i" >/dev/null; done; }
q() { curl -s --data-urlencode "query=$1" "$PROM/api/v1/query" | python3 -c "import sys,json; r=json.load(sys.stdin)['data']['result']; print(json.dumps([(x['metric'],x['value'][1]) for x in r]))"; }

PA=$(arrancar servicio-a 42201 http://localhost:42202 0); PIDS+=("$PA")
PB=$(arrancar servicio-b 42202 http://localhost:42201 0); PIDS+=("$PB")
esperar 42201; esperar 42202
for i in $(seq 1 60); do curl -sf "$PROM/-/ready" >/dev/null && break; sleep 1; done
for i in $(seq 1 90); do curl -sf "$GRAF/api/health" >/dev/null && break; sleep 1; done

# Grafana: origen de datos Prometheus y tablero provisionados por API
curl -sf -u admin:admin -H 'Content-Type: application/json' "$GRAF/api/datasources" \
  -d '{"name":"Prom curso","uid":"prom-curso","type":"prometheus","url":"http://host.docker.internal:42390","access":"proxy"}' > "$OUT/datasource.json" \
  || { echo "FALLÓ la creación del origen de datos de Grafana"; exit 1; }
python3 -c "import json; d=json.load(open('$OUT/datasource.json')); assert d['datasource']['uid']=='prom-curso', d"
echo "--- fase 1: sin latencia (carga durante más de tres intervalos de raspado, para tener línea base)"
fin=$((SECONDS+25)); i=0; while [ $SECONDS -lt $fin ]; do i=$((i+1)); curl -s "http://localhost:42201/a/pedido/$i" >/dev/null; sleep 0.1; done
sleep 6
echo "p95 cobro (normal):"; q 'histogram_quantile(0.95, sum by (le) (rate(pagos_cobro_duracion_seconds_bucket[30s])))' | tee "$OUT/p95-normal.txt"
python3 - <<PY | tee "$OUT/diag.txt"
import json
r=json.loads(open("$OUT/p95-normal.txt").read().strip().splitlines()[-1])
assert r, "sin resultado"
v=float(r[0][1])
assert v==v and v<0.3, f"línea base inválida: {v}"   # NaN != NaN
print(f"LINEA-BASE p95 normal del cobro = {v:.3f} s")
PY
curl -s "$PROM/api/v1/alerts" | python3 -c "import sys,json; a=[x for x in json.load(sys.stdin)['data']['alerts'] if x['labels']['alertname']=='LatenciaDeCobroAlta']; assert not a, a; print('LatenciaDeCobroAlta inactiva antes de inyectar la latencia')"
echo "--- fase 2: se activa la latencia artificial en el servicio B (el investigador no lo sabe)"
kill "$PB"; wait "$PB" 2>/dev/null || true
PB=$(arrancar servicio-b 42202 http://localhost:42201 450); PIDS+=("$PB"); esperar 42202
# La carga es la misma. Se genera tráfico unos 40 s para que la alerta pase de pending a firing.
fin=$((SECONDS+45)); i=100; while [ $SECONDS -lt $fin ]; do i=$((i+1)); curl -s "http://localhost:42201/a/pedido/$i" >/dev/null; done
sleep 8

echo "--- diagnóstico solo con métricas y trazas"
echo "1) p95 de http por servicio y ruta:" | tee -a "$OUT/diag.txt"
q 'histogram_quantile(0.95, sum by (le, application, uri) (rate(http_server_requests_seconds_bucket{uri=~"/a/pedido.*|/b/cobro.*"}[30s])))' | tee -a "$OUT/diag.txt"
echo "2) p95 del cobro (métrica técnica de B):" | tee -a "$OUT/diag.txt"
q 'histogram_quantile(0.95, sum by (le) (rate(pagos_cobro_duracion_seconds_bucket[30s])))' | tee -a "$OUT/diag.txt"
echo "3) trazas de más de 300 ms en Tempo:" | tee -a "$OUT/diag.txt"
for i in $(seq 1 20); do
  curl -sG "$TEMPO/api/search" --data-urlencode 'q={ duration > 300ms }' --data-urlencode 'limit=5' > "$OUT/tempo-search.json"
  python3 -c "import json,sys; sys.exit(0 if json.load(open('$OUT/tempo-search.json')).get('traces') else 1)" && break; sleep 3
done
TID=$(python3 -c "import json; print(json.load(open('$OUT/tempo-search.json'))['traces'][0]['traceID'])")
echo "traceID lenta: $TID" | tee -a "$OUT/diag.txt"
curl -s "$TEMPO/api/traces/$TID" > "$OUT/traza.json"
python3 - <<PY | tee -a "$OUT/diag.txt"
import json
t=json.load(open("$OUT/traza.json"))
spans=[]
for lote in t.get('batches', t.get('resourceSpans', [])):
    svc=[a['value']['stringValue'] for a in lote['resource']['attributes'] if a['key']=='service.name'][0]
    for ss in lote.get('scopeSpans', lote.get('instrumentationLibrarySpans', [])):
        for s in ss['spans']:
            spans.append((svc,s['name'],(int(s['endTimeUnixNano'])-int(s['startTimeUnixNano']))/1e6))
spans.sort(key=lambda x:-x[2])
for svc,n,ms in spans: print(f"  {svc:12s} {n:28s} {ms:8.1f} ms")
servicios={s for s,_,_ in spans}
assert servicios>={'servicio-a','servicio-b'}, servicios
# El span más largo que NO es solo contenedor: el hijo más profundo con duración alta
hoja=[s for s in spans if s[1]=='pagos.cobro'][0]
assert hoja[0]=='servicio-b' and hoja[2]>300, hoja
print("CULPABLE: span", hoja[1], "en", hoja[0], "=", round(hoja[2]),"ms")
PY

echo "--- alerta y Grafana"
for i in $(seq 1 20); do
  curl -s "$PROM/api/v1/alerts" > "$OUT/alertas.json"
  python3 -c "import json,sys; a=json.load(open('$OUT/alertas.json'))['data']['alerts']; sys.exit(0 if any(x['labels']['alertname']=='LatenciaDeCobroAlta' and x['state']=='firing' for x in a) else 1)" && break; sleep 3
  curl -s "http://localhost:42201/a/pedido/1" >/dev/null
done
python3 -c "
import json; a=json.load(open('$OUT/alertas.json'))['data']['alerts']
f=[x for x in a if x['labels']['alertname']=='LatenciaDeCobroAlta' and x['state']=='firing']
assert f, a
print('ALERTA firing: LatenciaDeCobroAlta ->', f[0]['annotations']['resumen'])"
curl -s "$PROM/api/v1/rules" | python3 -c "import sys,json; g=json.load(sys.stdin)['data']['groups']; print('reglas cargadas:', [r['name'] for x in g for r in x['rules']])" | tee -a "$OUT/diag.txt"
# Grafana: tablero y consulta a través del origen de datos
curl -sf -u admin:admin -H 'Content-Type: application/json' "$GRAF/api/dashboards/db" \
  -d "{\"dashboard\": $(cat grafana-dashboard.json), \"overwrite\": true}" | tee "$OUT/dashboard.json"; echo
curl -sf -u admin:admin "$GRAF/api/dashboards/uid/pedidos-obs" | python3 -c "import sys,json; d=json.load(sys.stdin)['dashboard']; print('tablero Grafana:', d['title'], len(d['panels']), 'paneles')"
# cada expresión de los paneles del tablero devuelve datos a través del origen de datos de Grafana
python3 -c "import json; [print(t['expr']) for p in json.load(open('grafana-dashboard.json'))['panels'] for t in p['targets']]" | while IFS= read -r e; do
  curl -sf -u admin:admin -G "$GRAF/api/datasources/proxy/uid/prom-curso/api/v1/query" --data-urlencode "query=$e" | python3 -c "import sys,json; r=json.load(sys.stdin); assert r['status']=='success' and r['data']['result'], r; v=[x['value'][1] for x in r['data']['result']]; assert any(x!='NaN' for x in v), v; print('  panel OK:', '$e'[:70])" || { echo "panel sin datos: $e"; exit 1; }
done
curl -sf -u admin:admin -G "$GRAF/api/datasources/proxy/uid/prom-curso/api/v1/query" --data-urlencode 'query=sum(pedidos_confirmados_total)' | python3 -c "import sys,json; r=json.load(sys.stdin); assert r['status']=='success' and r['data']['result'], r; print('consulta vía Grafana: pedidos confirmados =', r['data']['result'][0]['value'][1])"

echo "--- seguir una petición a través de dos servicios en los registros JSON"
T=0af7651916cd43dd8448eb211c80319c
curl -s -H "traceparent: 00-$T-b7ad6b7169203331-01" http://localhost:42201/a/pedido/999 >/dev/null; sleep 1
grep -l "$T" "$OUT"/servicio-*.log | tee "$OUT/seguimiento.txt"
test "$(grep -l "$T" "$OUT"/servicio-*.log | wc -l)" -eq 2
grep -h "$T" "$OUT"/servicio-*.log | python3 -c "
import sys,json
for l in sys.stdin:
    d=json.loads(l); print(' ', d['service']['name'], d['traceId'][:8], d['message'])"
echo ESCENARIO-OK
