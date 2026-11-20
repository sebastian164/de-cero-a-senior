#!/usr/bin/env bash
# Solución ejecutable del taller Kubernetes 04. Mide errores antes y después de cada cambio.
# El criterio final (diez minutos sin reinicios) es opcional: ESPERA_LARGA=1 ./probar.sh
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
preparar_cluster
SOL="$AQUI"
NS=bitacora
OUT="$SALIDAS/k8s-04.txt"; : > "$OUT"; : > "$SALIDAS/k8s-04-errores.txt"
kn() { k -n $NS "$@"; }
URL=http://bitacora-api/api/info

# parchea el Deployment con operaciones JSON
parche() { kn patch deploy bitacora-api --type=json -p "$1" >/dev/null; }
sin_anteriores() { [ "$(kn get pods -l app=bitacora-api --no-headers | wc -l | tr -d ' ')" = "$(kn get deploy bitacora-api -o jsonpath='{.spec.replicas}')" ]; }
# reinicia con el medidor corriendo y devuelve "peticiones errores"
medir_reinicio() {
  # «rollout status» vuelve cuando el último pod nuevo está listo, y en ese instante se borra el último pod de la versión anterior.
  # Si esa versión no tenía apagado ordenado, ese pod muere de golpe y la primera petición del medidor que le toque agota los 5 s (curl 28).
  # Se espera a que no quede ningún pod anterior (los Terminating también cuentan) para medir solo la versión que se acaba de desplegar.
  esperar_hasta 120 sin_anteriores
  sleep 5
  iniciar_medidor $NS $URL
  sleep 2
  kn rollout restart deploy/bitacora-api >/dev/null
  kn rollout status deploy/bitacora-api --timeout=240s >/dev/null
  sleep 28          # cubre los 20 s de arranque lento de los pods que no esperaban a estar listos
  resultados_medidor $NS
  kn logs medidor | awk -v p="${PASO_ACTUAL:--}" '$2 != "200" {print "paso", p, "epoch", $1, "código", $2, "salida_curl", $3}' >> "$SALIDAS/k8s-04-errores.txt" 2>/dev/null   # qué fallos hubo y cuándo
  detener_medidor $NS
}
un_pod() { [ "$(kn get pods -l app=bitacora-api --no-headers | wc -l | tr -d ' ')" = 1 ]; }
registrar() { echo "$1" | tee -a "$OUT" >/dev/null; nota "$1"; }

k delete ns $NS --ignore-not-found --wait >/dev/null 2>&1
k create ns $NS >/dev/null
herramientas $NS

titulo "Paso 1: sin sondas, arranque de 20 s"
k apply -n $NS -f "$SOL/api.yaml" >/dev/null
parche '[{"op":"remove","path":"/spec/template/spec/containers/0/startupProbe"},{"op":"remove","path":"/spec/template/spec/containers/0/readinessProbe"},{"op":"remove","path":"/spec/template/spec/containers/0/livenessProbe"},{"op":"remove","path":"/spec/template/spec/containers/0/lifecycle"},{"op":"replace","path":"/spec/template/spec/containers/0/env/1/value","value":"0"}]'
kn rollout status deploy/bitacora-api --timeout=240s >/dev/null; sleep 25
read -r t e <<< "$(medir_reinicio)"
registrar "paso1_sin_sondas: peticiones=$t errores=$e"
afirmar "sin sondas hay errores durante el reinicio ($e)" test "$e" -gt 0
E1=$e

titulo "Paso 2: solo sonda de disponibilidad"
parche '[{"op":"add","path":"/spec/template/spec/containers/0/readinessProbe","value":{"httpGet":{"path":"/salud/listo","port":"http"},"periodSeconds":2,"failureThreshold":2}}]'
kn rollout status deploy/bitacora-api --timeout=240s >/dev/null
read -r t e <<< "$(medir_reinicio)"
registrar "paso2_readiness: peticiones=$t errores=$e"
E2=$e

titulo "Paso 3: arranque real y las tres sondas"
kn get pods -l app=bitacora-api -o json | python3 -c '
import json,sys,datetime
f=lambda s: datetime.datetime.strptime(s,"%Y-%m-%dT%H:%M:%SZ")
d=[]
for p in json.load(sys.stdin)["items"]:
    ini=f(p["status"]["startTime"]); r=[c for c in p["status"]["conditions"] if c["type"]=="Ready"][0]
    d.append(int((f(r["lastTransitionTime"])-ini).total_seconds()))
print("segundos hasta Ready:",d)' | tee -a "$OUT" | sed 's/^/   /'
parche '[{"op":"add","path":"/spec/template/spec/containers/0/startupProbe","value":{"httpGet":{"path":"/salud/listo","port":"http"},"periodSeconds":2,"failureThreshold":30}},{"op":"add","path":"/spec/template/spec/containers/0/livenessProbe","value":{"httpGet":{"path":"/salud/vivo","port":"http"},"periodSeconds":5,"failureThreshold":3}}]'
kn rollout status deploy/bitacora-api --timeout=240s >/dev/null
read -r t e <<< "$(medir_reinicio)"
registrar "paso3_tres_sondas_sin_apagado: peticiones=$t errores=$e"
E3=$e

titulo "Paso 4: sonda de vida impaciente y sin sonda de arranque"
k apply -n $NS -f "$SOL/api.yaml" >/dev/null
parche '[{"op":"remove","path":"/spec/template/spec/containers/0/startupProbe"},{"op":"replace","path":"/spec/template/spec/containers/0/livenessProbe","value":{"httpGet":{"path":"/salud/listo","port":"http"},"periodSeconds":2,"failureThreshold":2}}]'
sleep 50
kn get pods -l app=bitacora-api --no-headers | sed 's/^/     /'
kn get events --field-selector reason=Unhealthy --sort-by=.lastTimestamp | tail -3 | sed 's/^/     /'
kn get events --field-selector reason=Killing --sort-by=.lastTimestamp | tail -2 | sed 's/^/     /'
reinicios="$(kn get pods -l app=bitacora-api -o jsonpath='{range .items[*]}{.status.containerStatuses[0].restartCount}{"\n"}{end}' | sort -n | tail -1)"
ev_vida="$(kn get events --field-selector reason=Unhealthy -o jsonpath='{range .items[*]}{.message}{"\n"}{end}' | grep -m1 'Liveness probe failed')"
registrar "paso4_vida_impaciente: reinicios_max_en_50s=$reinicios evento='$ev_vida'"
afirmar "hay un evento «Liveness probe failed» (la sonda de vida, no solo la de disponibilidad)" test -n "$ev_vida"
afirmar "el contenedor entra en ciclo de reinicios" test "$reinicios" -ge 1

titulo "Paso 5: apagado ordenado (ESPERA_APAGADO_MS y preStop)"
k apply -n $NS -f "$SOL/api.yaml" >/dev/null
parche '[{"op":"remove","path":"/spec/template/spec/containers/0/lifecycle"},{"op":"replace","path":"/spec/template/spec/containers/0/env/1/value","value":"0"}]'
kn rollout status deploy/bitacora-api --timeout=240s >/dev/null
tot5a=0; err5a=0
PASO_ACTUAL=5a
for i in 1 2; do read -r t e <<< "$(medir_reinicio)"; tot5a=$((tot5a+t)); err5a=$((err5a+e)); done
registrar "paso5_sondas_sin_apagado: peticiones=$tot5a errores=$err5a (2 reinicios)"
k apply -n $NS -f "$SOL/api.yaml" >/dev/null
kn rollout status deploy/bitacora-api --timeout=240s >/dev/null
# Se mide una sola vez (2 reinicios) y se exige cero. Cada error, si lo hubiera, queda en salidas/k8s-04-errores.txt con su código de salida de curl.
PASO_ACTUAL=5b; tot5b=0; err5b=0
for i in 1 2; do read -r t e <<< "$(medir_reinicio)"; tot5b=$((tot5b+t)); err5b=$((err5b+e)); done
registrar "paso5_final_con_apagado: peticiones=$tot5b errores=$err5b (2 reinicios)"
igual "cero errores con la versión final" 0 "$err5b"
nota "Pods finales:"; kn get pods -l app=bitacora-api --no-headers | sed 's/^/     /'
igual "ningún reinicio inesperado (RESTARTS)" 0 "$(kn get pods -l app=bitacora-api -o jsonpath='{range .items[*]}{.status.containerStatuses[0].restartCount}{"\n"}{end}' | sort -n | tail -1)"
for p in $(kn get pods -l app=bitacora-api -o name); do echo "$p $(kn get $p -o jsonpath='{.status.qosClass}')"; done | head -1 | tee -a "$OUT" | sed 's/^/   /'

titulo "Paso 6: memoria"
parche '[{"op":"replace","path":"/spec/template/spec/containers/0/resources/limits/memory","value":"20Mi"},{"op":"replace","path":"/spec/template/spec/containers/0/resources/requests/memory","value":"16Mi"}]'
sleep 45
kn get pods -l app=bitacora-api --no-headers | sed 's/^/     /'
est="$(kn get pods -l app=bitacora-api -o json | python3 -c '
import json,sys
for p in json.load(sys.stdin)["items"]:
    cs=p["status"]["containerStatuses"][0]
    s=cs.get("lastState",{}).get("terminated") or cs.get("state",{}).get("terminated")
    if s: print(s["reason"], s["exitCode"]); break')"
registrar "paso6_memoria_20Mi: lastState=$est"
igual "OOMKilled con código 137" "OOMKilled 137" "$est"
k apply -n $NS -f "$SOL/api.yaml" >/dev/null
kn rollout status deploy/bitacora-api --timeout=240s >/dev/null

titulo "Paso 7: CPU, límite de 100m contra sin límite"
medir_carga() { for i in 1 2 3; do dentro $NS curl -s -m 20 -w ' %{time_total}\n' "http://bitacora-api/api/carga?ms=300"; done | python3 -c '
import sys,json
r=[]
for l in sys.stdin:
    j,t=l.rsplit(" ",1); r.append((json.loads(j)["resultado"],float(t)))
print("tiempo_medio_s=%.3f trabajo_medio=%d" % (sum(x[1] for x in r)/len(r), sum(x[0] for x in r)/len(r)))'; }
parche '[{"op":"replace","path":"/spec/replicas","value":1}]'
kn rollout status deploy/bitacora-api --timeout=120s >/dev/null
esperar_hasta 90 un_pod
sleep 5
sin_limite="$(medir_carga)"
parche '[{"op":"add","path":"/spec/template/spec/containers/0/resources/limits/cpu","value":"100m"}]'
kn rollout status deploy/bitacora-api --timeout=240s >/dev/null
esperar_hasta 90 un_pod
sleep 5
con_limite="$(medir_carga)"
registrar "paso7_sin_limite: $sin_limite"
registrar "paso7_limite_100m: $con_limite"
tc="$(echo "$con_limite" | sed 's/tiempo_medio_s=\([0-9.]*\).*/\1/')"; ts="$(echo "$sin_limite" | sed 's/tiempo_medio_s=\([0-9.]*\).*/\1/')"
wc_="$(echo "$con_limite" | sed 's/.*trabajo_medio=//')"; ws="$(echo "$sin_limite" | sed 's/.*trabajo_medio=//')"
afirmar "con el límite se hace menos trabajo en los mismos 300 ms ($wc_ < $ws)" python3 -c "import sys; sys.exit(0 if $wc_ < $ws else 1)"
k apply -n $NS -f "$SOL/api.yaml" >/dev/null
kn rollout status deploy/bitacora-api --timeout=240s >/dev/null

titulo "Paso 8: más CPU de la que tiene cualquier nodo"
cat <<YAML | k apply -n $NS -f - >/dev/null
apiVersion: v1
kind: Pod
metadata: {name: demasiada-cpu}
spec:
  terminationGracePeriodSeconds: 0
  containers:
    - name: c
      image: busybox:1.37
      command: [sleep, "100000"]
      resources: {requests: {cpu: "64"}}
YAML
sleep 8
estado="$(kn get pod demasiada-cpu --no-headers | awk '{print $3}')"
msg="$(kn get events --field-selector involvedObject.name=demasiada-cpu -o jsonpath='{.items[0].message}')"
registrar "paso8_cpu_64: estado=$estado evento='$msg'"
igual "el pod queda Pending" Pending "$estado"
kn delete pod demasiada-cpu --now >/dev/null

titulo "Paso 9: clases de calidad de servicio"
k apply -n $NS -f "$SOL/qos.yaml" >/dev/null
kn wait --for=condition=Ready pod/qos-guaranteed pod/qos-burstable pod/qos-besteffort --timeout=90s >/dev/null
for p in guaranteed burstable besteffort; do
  c="$(kn get pod qos-$p -o jsonpath='{.status.qosClass}')"
  registrar "paso9_qos: qos-$p -> $c"
done
igual "Guaranteed" Guaranteed "$(kn get pod qos-guaranteed -o jsonpath='{.status.qosClass}')"
igual "Burstable" Burstable "$(kn get pod qos-burstable -o jsonpath='{.status.qosClass}')"
igual "BestEffort" BestEffort "$(kn get pod qos-besteffort -o jsonpath='{.status.qosClass}')"
igual "los pods de la API son Burstable" Burstable "$(kn get pods -l app=bitacora-api -o jsonpath='{.items[0].status.qosClass}')"

titulo "Criterio: ningún reinicio inesperado tras diez minutos con la versión final"
if [ "${ESPERA_LARGA:-0}" = 1 ]; then
nota "esperando 600 s..."
sleep 600
igual "RESTARTS tras 600 s de reposo" 0 "$(kn get pods -l app=bitacora-api -o jsonpath='{range .items[*]}{.status.containerStatuses[0].restartCount}{"\n"}{end}' | sort -n | tail -1)"
else
nota "(espera de diez minutos omitida: ESPERA_LARGA=1 la ejecuta)"
igual "RESTARTS con la versión final" 0 "$(kn get pods -l app=bitacora-api -o jsonpath='{range .items[*]}{.status.containerStatuses[0].restartCount}{"\n"}{end}' | sort -n | tail -1)"
fi

titulo "Limpieza"
k delete ns $NS --wait >/dev/null && ok "espacio de nombres borrado"
terminar "Kubernetes 04"
