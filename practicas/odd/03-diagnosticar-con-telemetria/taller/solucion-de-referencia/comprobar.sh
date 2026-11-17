#!/usr/bin/env bash
# Taller ODD 03: el simulacro. Puertos: 48400 (Grafana), 48401 (nginx), 48410-48412 (tres copias), 48413-48415 (copias auxiliares),
# 48417/48418 (OTLP), 48419/48420 (recolector con muestreo).
# Quien diagnostica (diagnosticar.py) solo usa la interfaz de observabilidad: Prometheus y Tempo a través de Grafana.
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
CPA="$C/otel-api.jar:$C/otel-context.jar:$C/otel-common.jar"
export VERSION_LGTM=${VERSION_LGTM:-0.40.0}
proyecto=pub-practicas-odd03
trabajo=$(mktemp -d)
marca="$trabajo/trafico.activo"
declare -a copias=()
HOST_IP=$(docker run --rm alpine getent ahostsv4 host.docker.internal 2>/dev/null | awk 'NR==1{print $1}')
export HOST_IP
compose() { (cd "$aqui" && docker compose -p "$proyecto" "$@"); }
parar_copias() {
  for p in "${copias[@]:-}"; do [ -n "$p" ] && kill -9 "$p" 2>/dev/null || true; done
  copias=()
}
limpiar() {
  rm -f "$marca"
  parar_copias
  for p in $(lsof -nP -tiTCP:48410-48415 -sTCP:LISTEN 2>/dev/null || true); do kill -9 "$p" 2>/dev/null || true; done
  compose --profile muestreo down -v >/dev/null 2>&1 || true
  chmod -R u+w "$trabajo" 2>/dev/null || true   # un disco "solo lectura" de un simulacro impediría borrar
  rm -rf "$trabajo"
}
trap limpiar EXIT
fallos=0
ok() { echo "  ok     $*"; }
falla() { echo "  FALLA  $*"; fallos=$((fallos + 1)); }
G=localhost:48400/api/datasources/proxy/uid
CLAVE="odd3-$RANDOM-$RANDOM"
prom() { curl -s -m 20 -u admin:admin --data-urlencode "query=$1" "$G/prometheus/api/v1/query"; }
valor() { prom "$1" | python3 -c "import sys,json;r=json.load(sys.stdin)['data']['result'];print(r[0]['value'][1] if r else 'NaN')"; }

# Arranca una copia: arrancar_copia <puerto> <servicio> <id de copia> <datos> [VAR=valor...]
arrancar_copia() {
  local puerto=$1 servicio=$2 id=$3 datos=$4; shift 4
  mkdir -p "$trabajo/$datos"
  (cd "$trabajo"; env OTEL_SERVICE_NAME="$servicio" OTEL_RESOURCE_ATTRIBUTES="service.instance.id=$id" OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:48418 \
     OTEL_METRIC_EXPORT_INTERVAL=5000 OTEL_BSP_SCHEDULE_DELAY=1000 OTEL_LOGS_EXPORTER=none PUERTO="$puerto" DIRECTORIO_DATOS="$trabajo/$datos" \
     CLAVE_API="$CLAVE" "$@" java -javaagent:"$C/agente.jar" -cp "$trabajo/clases:$CPA" Servidor >"$trabajo/$id.salida" 2>&1 </dev/null &
   echo $! >"$trabajo/$id.pid")
  copias+=("$(cat "$trabajo/$id.pid")")
  for _ in $(seq 1 100); do [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:$puerto/salud/listo)" = 200 ] && return 0; sleep 0.2; done
  echo "la copia $id no arrancó" >&2; return 1
}
# Las tres copias detrás de nginx, con un fallo activado "por configuración": reiniciarlas con otro entorno, sin tocar el código.
levantar_tres() {  # levantar_tres <fallo> <nivel de instrumentación> [VAR=valor para el exportador...]
  local fallo=$1 nivel=$2; shift 2
  parar_copias
  chmod -R u+w "$trabajo"/datos1 "$trabajo"/datos2 "$trabajo"/datos3 2>/dev/null || true
  # La lectura lenta depende de cuántas notas hay: las tres copias vuelven a su estado de partida (5, 50 y 150 notas) para que haya con qué comparar.
  [ "$fallo" = lectura_lenta ] && rm -f "$trabajo"/datos1/notas.txt "$trabajo"/datos2/notas.txt "$trabajo"/datos3/notas.txt
  local e1=() e2=() e3=()
  case $fallo in
    ninguno) ;;
    notas_largas) e1=(DEFECTO_NOTA_LARGA_MS=2000); e2=("${e1[@]}"); e3=("${e1[@]}") ;;
    errores_1_de_5) e1=(FALLOS=errores_1_de_5); e2=("${e1[@]}"); e3=("${e1[@]}") ;;
    disco_solo_lectura) ;;   # no es configuración de la API: se provoca de verdad al final (chmod sobre los datos de la copia 2)
    clave_distinta) e3=(CLAVE_API=otra-clave-que-no-es) ;;
    lectura_lenta) e1=(FALLOS=lectura_lenta); e2=("${e1[@]}"); e3=("${e1[@]}") ;;
  esac
  arrancar_copia 48410 bitacora-api copia-1 datos1 INSTRUMENTACION="$nivel" "$@" ${e1[@]+"${e1[@]}"}
  arrancar_copia 48411 bitacora-api copia-2 datos2 INSTRUMENTACION="$nivel" "$@" ${e2[@]+"${e2[@]}"}
  arrancar_copia 48412 bitacora-api copia-3 datos3 INSTRUMENTACION="$nivel" "$@" ${e3[@]+"${e3[@]}"}
  if [ "$fallo" = lectura_lenta ]; then
    local n=5 puerto i
    for puerto in 48410 48411 48412; do for i in $(seq 1 "$n"); do curl -s -o /dev/null -X POST -H "X-Clave: $CLAVE" -d "nota inicial $i" localhost:$puerto/api/notas; done; n=$((n == 5 ? 50 : 150)); done
  fi
  if [ "$fallo" = disco_solo_lectura ]; then
    # El disco de la copia 2 pasa a solo lectura DESPUÉS de arrancar (como pasa cuando un disco se degrada): ni la carpeta ni el archivo admiten escritura.
    chmod a-w "$trabajo/datos2/notas.txt" "$trabajo/datos2"
  fi
}
diagnosticar() { SERVICIO=${SERVICIO:-bitacora-api} python3 "$aqui/diagnosticar.py" 48400 "${VENTANA:-38}"; }
# Un fallo: activar, dejar que el tráfico lo exponga, diagnosticar. Escribe una fila de la tabla.
simulacro() {  # simulacro <fallo> <nivel> [VAR=valor...]
  local fallo=$1 nivel=$2; shift 2
  local t0 t1 res diag
  levantar_tres "$fallo" "$nivel" "$@"
  sleep "${ESPERA:-40}"   # espera fija: que el tráfico exponga el fallo y la telemetría llegue; no cuenta como tiempo de diagnóstico
  t0=$(date +%s)
  res=$(diagnosticar)
  t1=$(date +%s)
  diag=$(python3 -c "import sys,json;print(json.loads(sys.argv[1])['diagnostico'])" "$res")
  echo "$res" >"$trabajo/res-$fallo-$nivel.json"
  echo "  [instrumentación $nivel] fallo activado: ${fallo} -> diagnóstico: $diag ($((t1 - t0)) s de diagnóstico, sin contar la espera fija de ${ESPERA:-40} s; $(python3 -c "import sys,json;print(len(json.loads(sys.argv[1])['consultas']))" "$res") consultas)"
  python3 -c "import sys,json;d=json.loads(sys.argv[1]);print('         causa:',d['causa']);print('         síntoma:',d['sintoma']);print('         evidencia:',d.get('evidencia'))" "$res"
  RESULTADO_DIAG=$diag; TIEMPO_DIAG=$((t1 - t0)); CONSULTAS_DIAG=$(python3 -c "import sys,json;print(len(json.loads(sys.argv[1])['consultas']))" "$res")
}

javac -cp "$CPA" -d "$trabajo/clases" "$aqui/Servidor.java"

echo "== Entorno: grafana/otel-lgtm:$VERSION_LGTM, nginx con módulo OpenTelemetry, tres copias de la API"
compose up -d --wait observabilidad web >/dev/null 2>&1 || { echo "no levantó el entorno"; exit 1; }
for _ in $(seq 1 90); do
  [ "$(curl -s -m 3 -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' localhost:48418/v1/traces)" = 200 ] \
    && [ "$(curl -s -m 3 -u admin:admin "$G/tempo/ready")" = ready ] && break
  sleep 2
done

echo "== Paso 1: pruebas de la instrumentación"
arrancar_copia 48413 bitacora-api-prueba copia-prueba datos-prueba INSTRUMENTACION=2
curl -s -o /dev/null -X POST -H "X-Clave: $CLAVE" -H 'X-Correlacion: prueba-unica-1' -d "nota de la prueba" localhost:48413/api/notas
sleep 1
ev=$(grep '"correlacion":"prueba-unica-1"' "$trabajo/copia-prueba.salida")
cuenta_pet=$(grep -c '"evento":"peticion".*"correlacion":"prueba-unica-1"' "$trabajo/copia-prueba.salida" || true)
cuenta_nota=$(grep -c '"evento":"nota.guardada".*"correlacion":"prueba-unica-1"' "$trabajo/copia-prueba.salida" || true)
[ "$cuenta_pet" = 1 ] && [ "$cuenta_nota" = 1 ] && ok "guardar una nota emite exactamente un evento peticion y un evento nota.guardada" || falla "eventos emitidos: peticion=$cuenta_pet nota.guardada=$cuenta_nota"
python3 - "$ev" <<'PY' && ok "el evento de petición trae todos sus campos con los valores esperados (estado 201, resultado exito, trace_id de 32 hex, sin contenido de la nota)" || falla "campos del evento"
import sys,json,re
lineas=[json.loads(l) for l in sys.argv[1].splitlines()]
p=[l for l in lineas if l['evento']=='peticion'][0]
for c in ['ts','correlacion','metodo','ruta','estado','resultado','duracion_ms','host','version','servicio','nota_longitud','trace_id','span_id']:
    assert c in p, c
assert p['estado']==201 and p['resultado']=='exito' and p['metodo']=='POST' and p['ruta']=='/api/notas' and p['nota_longitud']==17
assert re.fullmatch(r'[0-9a-f]{32}',p['trace_id']) and re.fullmatch(r'[0-9a-f]{16}',p['span_id'])
assert 'nota de la prueba' not in json.dumps(lineas)
PY
tid=$(python3 -c "import sys,json;print([json.loads(l) for l in sys.argv[1].splitlines() if json.loads(l)['evento']=='peticion'][0]['trace_id'])" "$ev")
for _ in $(seq 1 40); do
  nombres=$(curl -s -u admin:admin "$G/tempo/api/traces/$tid" | python3 -c "
import sys,json
try:
    d=json.load(sys.stdin); print(' '.join(sorted(s['name'] for b in d['batches'] for ss in b['scopeSpans'] for s in ss['spans'])))
except Exception: print('')")
  [ -n "$nombres" ] && break; sleep 3
done
[ "$nombres" = "POST /api/notas notas.escribir" ] && ok "la traza de esa petición tiene los tramos esperados: $nombres" || falla "tramos de la traza: '$nombres'"
parar_copias

echo "== Datos de partida: 5, 50 y 150 notas en las copias 1, 2 y 3 (para que haya con qué comparar), y tráfico continuo por nginx"
levantar_tres ninguno 1
n=5; for puerto in 48410 48411 48412; do for i in $(seq 1 "$n"); do curl -s -o /dev/null -X POST -H "X-Clave: $CLAVE" -d "nota inicial $i" localhost:$puerto/api/notas; done; n=$((n == 5 ? 50 : 150)); done
touch "$marca"
"$aqui/trafico.sh" 48401 "$marca" "$CLAVE" >/dev/null 2>&1 &
sleep 20
echo "== Paso 3: el simulacro. El orden lo sortea el script con la semilla ${SEMILLA:-7}; quien diagnostica no lo conoce."
RANDOM=${SEMILLA:-7}
orden=$(printf '%s\n' notas_largas errores_1_de_5 disco_solo_lectura clave_distinta lectura_lenta | while read -r f; do echo "$RANDOM $f"; done | sort -n | awk '{print $2}')
echo "  orden sorteado: $(echo $orden | tr ' ' ',')"
echo "== Pasos 3 y 4: simulacro con el nivel 1 de instrumentación"
echo "        (nivel 1 = la instrumentación de la sesión 02 SIN los atributos bitacora.nota.longitud y bitacora.notas.total: una regresión deliberada,"
echo "         porque la solución de la sesión 02 ya los traía; sirve para ejercitar el paso 5, no es un hallazgo sobre la sesión 02)"
declare -a huecos=()
tabla="| Fallo | Diagnóstico | Segundos de diagnóstico | Consultas | Resultado |\n|---|---|---|---|---|\n"
for f in $orden; do
  simulacro "$f" 1
  if [ "$RESULTADO_DIAG" = "$f" ]; then ok "$f diagnosticado con la interfaz en ${TIEMPO_DIAG} s"; tabla+="| $f | $RESULTADO_DIAG | $TIEMPO_DIAG | $CONSULTAS_DIAG | acertó |\n"
  else echo "  hueco  $f no se pudo cerrar (dijo: $RESULTADO_DIAG)"; huecos+=("$f"); tabla+="| $f | sin diagnóstico ($RESULTADO_DIAG) | - | $CONSULTAS_DIAG | no se pudo |\n"; fi
done
printf "$tabla" >"$trabajo/tabla-nivel1.md"; echo; printf "$tabla" | sed 's/^/        /'; echo

echo "== Paso 5: cerrar los huecos (atributos bitacora.nota.longitud y bitacora.notas.total) y repetir esos fallos"
[ "${#huecos[@]}" -gt 0 ] && ok "huecos encontrados por el simulacro: ${huecos[*]}" || falla "el simulacro no encontró ningún hueco (se esperaba al menos uno)"
for f in ${huecos[@]+"${huecos[@]}"}; do
  simulacro "$f" 2
  [ "$RESULTADO_DIAG" = "$f" ] && ok "$f ahora se diagnostica en ${TIEMPO_DIAG} s y ${CONSULTAS_DIAG} consultas (antes: sin diagnóstico)" || falla "$f sigue sin diagnosticarse con la instrumentación completa ($RESULTADO_DIAG)"
done

echo "== Paso 6: alertas. Reglas validadas con promtool y una disparada de verdad en Grafana"
docker cp "$aqui/alertas.yaml" "${proyecto}-observabilidad-1:/tmp/alertas.yaml"
salida=$(docker exec "${proyecto}-observabilidad-1" /otel-lgtm/prometheus/promtool check rules /tmp/alertas.yaml 2>&1) && ok "promtool: $(echo "$salida" | tr '\n' ' ')" || { falla "promtool rechazó las reglas"; echo "$salida"; }
levantar_tres errores_1_de_5 2
carpeta=$(curl -s -m 10 -u admin:admin -H 'Content-Type: application/json' -d '{"title":"bitacora-taller"}' localhost:48400/api/folders | python3 -c "import sys,json;print(json.load(sys.stdin)['uid'])")
regla=$(python3 - "$carpeta" <<'PY'
import sys,json
expr='1 - sum(increase(bitacora_duracion_milliseconds_bucket{ruta="/api/notas",metodo="POST",clase_estado="2xx",le="500"}[1m])) / sum(increase(bitacora_duracion_milliseconds_count{ruta="/api/notas",metodo="POST"}[1m]))'
print(json.dumps({"interval":10,"rules":[{
 "title":"GuardarNotaGastaElPresupuesto","folderUID":sys.argv[1],"ruleGroup":"bitacora","orgID":1,"for":"0s","noDataState":"OK","execErrState":"Error","condition":"C",
 "data":[
  {"refId":"A","relativeTimeRange":{"from":300,"to":0},"datasourceUid":"prometheus","model":{"expr":expr,"instant":True,"refId":"A"}},
  {"refId":"B","datasourceUid":"__expr__","model":{"type":"reduce","expression":"A","reducer":"last","refId":"B"}},
  {"refId":"C","datasourceUid":"__expr__","model":{"type":"threshold","expression":"B","conditions":[{"evaluator":{"type":"gt","params":[0.144]}}],"refId":"C"}}],
 "annotations":{"resumen":"Guardar una nota gasta el presupuesto 14 veces más rápido de lo permitido"},"labels":{"severity":"page"}}]}))
PY
)
curl -s -m 10 -u admin:admin -X PUT -H 'Content-Type: application/json' -d "$regla" "localhost:48400/api/v1/provisioning/folder/$carpeta/rule-groups/bitacora" | head -c 150; echo
estado=inactivo
for _ in $(seq 1 24); do
  estado=$(curl -s -m 10 -u admin:admin localhost:48400/api/prometheus/grafana/api/v1/rules | python3 -c "
import sys,json
d=json.load(sys.stdin)
r=[x for g in d['data']['groups'] for x in g['rules'] if x['name']=='GuardarNotaGastaElPresupuesto']
print(r[0]['state'] if r else 'sin regla')")
  [ "$estado" = firing ] && break; sleep 10
done
[ "$estado" = firing ] && ok "la alerta GuardarNotaGastaElPresupuesto está en estado firing con el fallo errores_1_de_5 activo (Grafana la evaluó sola cada 10 s)" || falla "la alerta no disparó (estado: $estado)"
curl -s -m 10 -u admin:admin -X DELETE "localhost:48400/api/v1/provisioning/folder/$carpeta/rule-groups/bitacora" >/dev/null || true

echo "== Paso 7: una alerta mala (CPU)"
cpu_metrica=$(curl -s -m 10 -u admin:admin "$G/prometheus/api/v1/label/__name__/values" | python3 -c "import sys,json;print([n for n in json.load(sys.stdin)['data'] if 'cpu' in n and 'utilization' in n])")
echo "        métricas de CPU disponibles: $cpu_metrica"
levantar_tres ninguno 2
sleep 8
# Caso A: el CPU sube y el usuario no sufre. Ocho peticiones de /api/carga a la vez gastan CPU; guardar y leer siguen igual de rápidos.
cargas=(); for _ in 1 2 3 4 5 6 7 8; do curl -s -m 15 -o /dev/null "localhost:48410/api/carga?ms=5000" & cargas+=($!); done
sleep 14
cpuA=$(valor 'max(max_over_time(jvm_cpu_recent_utilization_ratio{service_name="bitacora-api"}[20s]))')
erroresA=$(valor 'sum(increase(bitacora_peticiones_total{service_name="bitacora-api",clase_estado="5xx"}[20s]))')
p95A=$(curl -s -m 20 -u admin:admin --data-urlencode 'query=histogram_quantile(0.95, sum by (le) (increase(bitacora_duracion_milliseconds_bucket{service_name="bitacora-api",ruta="/api/notas",metodo="POST"}[20s])))' "$G/prometheus/api/v1/query" | python3 -c "import sys,json;r=json.load(sys.stdin)['data']['result'];print(r[0]['value'][1] if r else 'NaN')")
wait "${cargas[@]}"   # solo las ocho cargas: un wait a secas esperaría también al generador de tráfico, que no termina
echo "        A) CPU máx. = $cpuA (regla mala: CPU > 0.1), 5xx = $erroresA, p95 de guardar = $p95A ms"
python3 -c "import sys;c=float(sys.argv[1]);sys.exit(0 if c>0.1 else 1)" "$cpuA" && ok "A) la alerta de CPU suena (CPU $cpuA > 0.1) y los usuarios no sufren: guardar sigue en $p95A ms y no hay 5xx" || falla "A) el CPU no superó el umbral (valor $cpuA)"
# Caso B: el usuario sufre y la alerta no suena. Disco de solo lectura en una copia: guardar falla, el CPU está en reposo.
levantar_tres disco_solo_lectura 2
sleep 25
cpuB=$(valor 'max(max_over_time(jvm_cpu_recent_utilization_ratio{service_name="bitacora-api",service_instance_id="copia-2"}[20s]))')
errB=$(valor 'sum(increase(bitacora_peticiones_total{service_name="bitacora-api",service_instance_id="copia-2",ruta="/api/notas",metodo="POST",clase_estado="5xx"}[20s])) / sum(increase(bitacora_peticiones_total{service_name="bitacora-api",service_instance_id="copia-2",ruta="/api/notas",metodo="POST"}[20s]))')
echo "        B) CPU de la copia con el disco roto = $cpuB, proporción de 5xx al guardar en esa copia = $errB"
python3 -c "import sys;c,e=float(sys.argv[1]),float(sys.argv[2]);sys.exit(0 if c<0.1 and e>0.9 else 1)" "$cpuB" "$errB" && ok "B) el 100 % de las escrituras de esa copia falla y el CPU ($cpuB) no llega a 0.1: la alerta de CPU no suena" || falla "B) no se reprodujo el caso"

echo "== Paso 8: costo y muestreo"
compose --profile muestreo up -d --wait colector >/dev/null 2>&1 || falla "no arrancó el recolector"
sleep 3
levantar_tres errores_1_de_5 2
pids_extra=()
trafico_directo() { # 150 ciclos de tráfico contra una copia auxiliar
  local puerto=$1; local larga; larga=$(printf 'z%.0s' $(seq 1 450))
  for i in $(seq 1 150); do
    curl -s -o /dev/null -X POST -H "X-Clave: $CLAVE" -d "n $i" localhost:$puerto/api/notas
    curl -s -o /dev/null localhost:$puerto/api/notas
    curl -s -o /dev/null localhost:$puerto/api/info
    [ $((i % 10)) = 0 ] && curl -s -o /dev/null -X POST -H "X-Clave: $CLAVE" -d "$larga" localhost:$puerto/api/notas
  done
}
rm -f "$marca"; sleep 2
arrancar_copia 48414 bitacora-api-completo copia-aux-1 datos-aux1 INSTRUMENTACION=2 DEFECTO_NOTA_LARGA_MS=1000 FALLOS=errores_1_de_5
arrancar_copia 48415 bitacora-api-muestreado copia-aux-2 datos-aux2 INSTRUMENTACION=2 DEFECTO_NOTA_LARGA_MS=1000 FALLOS=errores_1_de_5 OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:48419/v1/traces
trafico_directo 48414 & p1=$!; trafico_directo 48415 & p2=$!; wait "$p1" "$p2"
sleep 35
contar() { curl -s -m 30 -u admin:admin -G --data-urlencode "q=$1" --data-urlencode limit=3000 "$G/tempo/api/search" | python3 -c "import sys,json;print(len(json.load(sys.stdin).get('traces',[])))"; }
total_c=$(contar '{ resource.service.name = "bitacora-api-completo" }'); total_m=$(contar '{ resource.service.name = "bitacora-api-muestreado" }')
len_c=$(contar '{ resource.service.name = "bitacora-api-completo" && duration > 500ms }'); len_m=$(contar '{ resource.service.name = "bitacora-api-muestreado" && duration > 500ms }')
err_c=$(contar '{ resource.service.name = "bitacora-api-completo" && status = error }'); err_m=$(contar '{ resource.service.name = "bitacora-api-muestreado" && status = error }')
met=$(curl -s -m 10 localhost:48420/metrics)
rec=$(grep -E '^otelcol_receiver_accepted_spans' <<<"$met" | awk '{s+=$NF} END{print s+0}')
env_=$(grep -E '^otelcol_exporter_sent_spans' <<<"$met" | awk '{s+=$NF} END{print s+0}')
reqs=$(valor 'sum(increase(bitacora_peticiones_total{service_name="bitacora-api-completo"}[10m]))')
echo "        mismo tráfico (450 peticiones a cada copia): trazas en Tempo: sin muestreo = $total_c, con muestreo = $total_m"
echo "        trazas con error: sin muestreo = $err_c, con muestreo = $err_m; trazas de más de 500 ms: sin muestreo = $len_c, con muestreo = $len_m"
echo "        recolector: tramos recibidos = $rec, tramos enviados = $env_"
python3 - "$total_c" "$total_m" "$err_c" "$err_m" "$rec" "$env_" "$len_c" "$len_m" <<'PY' && ok "el muestreo por cola conserva el 100 % de las trazas con error y de las lentas, y baja el volumen" || falla "el muestreo no cumple lo esperado"
import sys
tc,tm,ec,em,rec,env,lc,lm=map(float,sys.argv[1:])
print(f"        volumen de trazas: {(1-tm/tc)*100:.0f} % menos; tramos exportados: {(1-env/rec)*100:.0f} % menos")
sys.exit(0 if ec>0 and lc>0 and em==ec and lm==lc and tm<tc*0.6 else 1)
PY
parar_copias
compose --profile muestreo stop colector >/dev/null 2>&1 || true

echo "== Paso 8 (continuación): ¿se siguen diagnosticando los cinco fallos con el muestreo?"
touch "$marca"
"$aqui/trafico.sh" 48401 "$marca" "$CLAVE" >/dev/null 2>&1 &
compose --profile muestreo up -d --wait colector >/dev/null 2>&1 || falla "no arrancó el recolector"
for f in $orden; do
  simulacro "$f" 2 OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:48419/v1/traces
  [ "$RESULTADO_DIAG" = "$f" ] && ok "con muestreo, $f se sigue diagnosticando ($TIEMPO_DIAG s)" || falla "con muestreo, $f NO se diagnostica ($RESULTADO_DIAG)"
done
rm -f "$marca"
[ "$fallos" -eq 0 ]
