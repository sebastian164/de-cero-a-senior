#!/usr/bin/env bash
# Taller 05, parte 2: las alertas y el panel contra un Prometheus y un Grafana REALES.
#   1. promtool valida alertas.yaml.  2. Una instancia con /actuator/prometheus y el servicio externo a 3 s (la situación "retención").
#   3. Prometheus (puerto 46909) lee la instancia cada segundo y evalúa alertas.yaml tal cual: las dos alertas deben pasar a firing.
#   4. Las series que usan las alertas y el panel existen con esos nombres, y el percentil 95 de usage se puede calcular.
#   5. Grafana (puerto 46300) recibe panel.json por su API, ejecuta cada consulta del panel y se guarda el JSON realmente exportado.
# Se llama desde medir.sh (que deja levantado el servicio externo en 46799) y añade sus criterios a resultados/resultados.md.
set -euo pipefail
source "$(dirname "$0")/herramientas/lib.sh"
AQUI="$RAIZ_VER"
OUT="$AQUI/resultados"; mkdir -p "$OUT/prometheus"
R="$OUT/resultados.md"
JAR="$AQUI/target/taller-05-1.0.0.jar"
export DB_PASSWORD='Tienda2026!'
fallos=0
criterio() { if eval "$2"; then echo "   ok    $1"; echo "- [x] $1" >> "$R"; else echo "   FALLA $1"; echo "- [ ] **FALLA** $1" >> "$R"; fallos=$((fallos+1)); fi; }
PROM_IMG=prom/prometheus:v3.16.0
GRAF_IMG=grafana/otel-lgtm:0.40.0
PID=""; K6=""
limpiar() {
  [ -n "$K6" ] && kill "$K6" 2> /dev/null || true
  [ -n "$PID" ] && { kill "$PID" 2> /dev/null || true; wait "$PID" 2> /dev/null || true; }
  docker rm -f ${PROYECTO}-prometheus ${PROYECTO}-grafana > /dev/null 2>&1 || true
  curl -s "localhost:46799/demora?ms=50" > /dev/null || true
}
trap limpiar EXIT
docker rm -f ${PROYECTO}-prometheus ${PROYECTO}-grafana > /dev/null 2>&1 || true
{ echo; echo "## Parte 2: alertas y panel contra Prometheus y Grafana reales"; echo; } >> "$R"

echo "== promtool check rules"
docker run --rm --entrypoint promtool -v "$AQUI/alertas.yaml:/alertas.yaml:ro" "$PROM_IMG" check rules /alertas.yaml > "$OUT/prometheus/promtool.txt" 2>&1 || true
cat "$OUT/prometheus/promtool.txt"
criterio "alertas.yaml pasa promtool check rules ($(grep -o '[0-9]* rules found' "$OUT/prometheus/promtool.txt"))" 'grep -q "SUCCESS: 2 rules found" "$OUT/prometheus/promtool.txt"'
PROM_VER=$(docker run --rm --entrypoint prometheus "$PROM_IMG" --version 2>&1 | head -1)

echo "== una instancia con /actuator/prometheus"
java -jar "$JAR" --server.port=46830 --spring.datasource.url="$DB_URL" > "$OUT/prometheus/instancia.log" 2>&1 &
PID=$!
for _ in $(seq 1 120); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:46830/actuator/health)" != "000" ] && break; sleep 0.5; done
for _ in $(seq 1 30); do curl -s -o /dev/null localhost:46830/lectura/1; done
curl -s localhost:46830/actuator/prometheus > "$OUT/prometheus/actuator-prometheus.txt"
criterio "la aplicación expone /actuator/prometheus con el nombre del pool (hikaricp_connections_active{pool=\"servicio\"})" 'grep -q "^hikaricp_connections_active{.*pool=\"servicio\"" "$OUT/prometheus/actuator-prometheus.txt"'
criterio "con percentiles-histogram hay cubetas de acquire y usage (hikaricp_connections_acquire_seconds_bucket, hikaricp_connections_usage_seconds_bucket)" \
  'grep -q "^hikaricp_connections_acquire_seconds_bucket" "$OUT/prometheus/actuator-prometheus.txt" && grep -q "^hikaricp_connections_usage_seconds_bucket" "$OUT/prometheus/actuator-prometheus.txt"'

cat > "$OUT/prometheus/prometheus.yml" <<YML
global:
  scrape_interval: 1s
  evaluation_interval: 1s
rule_files:
  - /etc/prometheus/alertas.yaml
scrape_configs:
  - job_name: servicio
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:46830"]
YML
docker run -d --name ${PROYECTO}-prometheus -p 46909:9090 \
  -v "$OUT/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro" -v "$AQUI/alertas.yaml:/etc/prometheus/alertas.yaml:ro" "$PROM_IMG" > /dev/null
for _ in $(seq 1 60); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:46909/-/ready)" = 200 ] && break; sleep 1; done
consulta() { curl -s --data-urlencode "query=$1" localhost:46909/api/v1/query; }
for _ in $(seq 1 30); do [ "$(consulta 'up{job="servicio"}' | jq -r '.data.result[0].value[1] // 0')" = 1 ] && break; sleep 1; done

echo "== situación: el servicio externo tarda 3 s con 150 peticiones/s durante 100 s"
curl -s "localhost:46799/demora?ms=3000" > /dev/null
BASES=http://localhost:46830 RATE=150 DURACION=100s HIST=0 k6 run -q "$RAIZ_VER/herramientas/carga05.js" > "$OUT/prometheus/k6.txt" 2>&1 &
K6=$!
grave=""; temprana=""; inicio=$(date +%s)
for _ in $(seq 1 120); do
  alertas=$(curl -s localhost:46909/api/v1/alerts)
  [ -z "$grave" ] && [ "$(jq -r '[.data.alerts[] | select(.labels.alertname == "PoolSinConexionesATiempo" and .state == "firing")] | length' <<< "$alertas")" -gt 0 ] && grave=$(( $(date +%s) - inicio ))
  [ -z "$temprana" ] && [ "$(jq -r '[.data.alerts[] | select(.labels.alertname == "PoolConHilosEnEspera" and .state == "firing")] | length' <<< "$alertas")" -gt 0 ] && temprana=$(( $(date +%s) - inicio ))
  [ -n "$grave" ] && [ -n "$temprana" ] && break
  sleep 1
done
curl -s localhost:46909/api/v1/alerts | jq . > "$OUT/prometheus/alertas-activas.json"
echo "   grave en ${grave:-nunca} s, temprana en ${temprana:-nunca} s"
criterio "Prometheus real: la alerta grave PoolSinConexionesATiempo pasa a firing (a los ${grave:-nunca} s de empezar la carga)" '[ -n "$grave" ]'
criterio "Prometheus real: la alerta temprana PoolConHilosEnEspera pasa a firing tras su ventana de 1 minuto (a los ${temprana:-nunca} s)" '[ -n "$temprana" ] && [ "$temprana" -ge 60 ]'

SERIES=(hikaricp_connections_active hikaricp_connections_idle hikaricp_connections_pending hikaricp_connections_max hikaricp_connections_timeout_total
        hikaricp_connections_acquire_seconds_sum hikaricp_connections_acquire_seconds_count hikaricp_connections_acquire_seconds_bucket
        hikaricp_connections_usage_seconds_sum hikaricp_connections_usage_seconds_count hikaricp_connections_usage_seconds_bucket
        hikaricp_connections_creation_seconds_count http_server_requests_seconds_sum)
faltan=""
for s in "${SERIES[@]}"; do
  n=$(consulta "count($s{pool=\"servicio\"})" | jq -r '.data.result[0].value[1] // 0')
  [ "$s" = http_server_requests_seconds_sum ] && n=$(consulta "count($s)" | jq -r '.data.result[0].value[1] // 0')
  [ "$n" = 0 ] && faltan="$faltan $s"
done
criterio "las ${#SERIES[@]} series que usan las alertas y el panel existen en Prometheus con esos nombres (faltan:${faltan:- ninguna})" '[ -z "$faltan" ]'
p95_usage=$(consulta 'histogram_quantile(0.95, sum by (le) (rate(hikaricp_connections_usage_seconds_bucket{pool="servicio"}[1m])))' | jq -r '.data.result[0].value[1] // "NaN"')
p95_acquire=$(consulta 'histogram_quantile(0.95, sum by (le) (rate(hikaricp_connections_acquire_seconds_bucket{pool="servicio"}[1m])))' | jq -r '.data.result[0].value[1] // "NaN"')
echo "   p95 usage ${p95_usage} s, p95 acquire ${p95_acquire} s"
criterio "histogram_quantile(0.95, ...) da el percentil 95 de usage ($p95_usage s, la retención de ~3 s) y de acquire ($p95_acquire s, el connection-timeout de 1 s)" \
  'awk -v u="$p95_usage" -v a="$p95_acquire" "BEGIN{exit !(u > 1 && u < 6 && a > 0.5 && a <= 1.5)}"'
{ echo; echo "Prometheus: $PROM_VER. Situación: servicio externo a 3 s, 150 peticiones/s, pool de 6, connection-timeout 1000 ms. Alerta grave en firing a los ${grave:-nunca} s y temprana a los ${temprana:-nunca} s. p95 de usage $p95_usage s; p95 de acquire $p95_acquire s."; } >> "$R"
kill "$K6" 2> /dev/null || true; wait "$K6" 2> /dev/null || true; K6=""

echo "== Grafana real: importar panel.json y ejecutar sus consultas"
docker run -d --name ${PROYECTO}-grafana -p 46300:3000 "$GRAF_IMG" > /dev/null
for _ in $(seq 1 180); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:46300/api/health)" = 200 ] && break; sleep 1; done
G="-u admin:admin -H Content-Type:application/json"
GRAF_VER=$(curl -s localhost:46300/api/health | jq -r .version)
# shellcheck disable=SC2086
curl -s $G -X POST localhost:46300/api/datasources -d '{"name":"Prometheus del taller","uid":"prom-taller","type":"prometheus","access":"proxy","url":"http://host.docker.internal:46909","isDefault":true}' > "$OUT/prometheus/datasource.json"
# shellcheck disable=SC2086
curl -s $G -X POST localhost:46300/api/dashboards/db -d "$(jq '{dashboard: ., overwrite: true, folderId: 0}' "$AQUI/panel.json")" > "$OUT/prometheus/importar.json"
criterio "Grafana $GRAF_VER importa panel.json por su API ($(jq -r '.status // .message' "$OUT/prometheus/importar.json"))" '[ "$(jq -r .status "$OUT/prometheus/importar.json")" = success ]'
# shellcheck disable=SC2086
curl -s $G localhost:46300/api/dashboards/uid/pool-servicio | jq '.dashboard' > "$OUT/prometheus/panel-exportado.json"
criterio "el panel exportado desde Grafana tiene los $(jq '.panels | length' "$AQUI/panel.json") paneles y los 2 percentiles 95 (histogram_quantile)" \
  '[ "$(jq ".panels | length" "$OUT/prometheus/panel-exportado.json")" = "$(jq ".panels | length" "$AQUI/panel.json")" ] && [ "$(grep -o "histogram_quantile(0.95" "$OUT/prometheus/panel-exportado.json" | wc -l | tr -d " ")" = 2 ]'
ahora=$(date +%s)000; desde=$(( $(date +%s) - 150 ))000
sinDatos=""
while IFS=$'\t' read -r titulo ref expr; do
  cuerpo=$(jq -n --arg e "$expr" --arg r "$ref" --argjson f "$desde" --argjson t "$ahora" \
    '{queries:[{refId:$r,datasource:{type:"prometheus",uid:"prom-taller"},expr:$e,range:true,intervalMs:5000,maxDataPoints:200}],from:($f|tostring),to:($t|tostring)}')
  # shellcheck disable=SC2086
  series=$(curl -s $G -X POST localhost:46300/api/ds/query -d "$cuerpo" | jq -r --arg r "$ref" '[.results[$r].frames[]? | select((.data.values[1] // []) | length > 0)] | length')
  echo "   panel «${titulo}» consulta ${ref}: ${series} serie(s)"
  [ "${series:-0}" -gt 0 ] || sinDatos="$sinDatos [$titulo/$ref]"
done < <(jq -r '.panels[] | .title as $t | .targets[] | [$t, .refId, .expr] | @tsv' "$AQUI/panel.json")
criterio "cada consulta del panel, ejecutada por Grafana contra Prometheus, devuelve series (sin datos:${sinDatos:- ninguna})" '[ -z "$sinDatos" ]'
{ echo; echo "Grafana: $GRAF_VER (imagen $GRAF_IMG). Panel importado por la API, exportado de vuelta en resultados/prometheus/panel-exportado.json."; } >> "$R"
limpiar
[ "$fallos" -eq 0 ] || { echo "prometheus.sh: $fallos criterios fallaron"; exit 1; }
echo "prometheus.sh: todo correcto"
