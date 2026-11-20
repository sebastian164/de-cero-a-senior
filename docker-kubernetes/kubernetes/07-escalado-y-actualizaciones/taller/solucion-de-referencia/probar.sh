#!/usr/bin/env bash
# Solución ejecutable del taller Kubernetes 07: escalado, despliegues malos y mantenimiento de nodos.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
preparar_cluster
instalar_metrics_server
SOL="$AQUI"
NS=bitacora-07
OUT="$SALIDAS/k8s-07.txt"; : > "$OUT"
kn() { k -n $NS "$@"; }
registrar() { echo "$1" >> "$OUT"; nota "$1"; }
parche() { kn patch deploy bitacora-api --type=json -p "$1" >/dev/null; }
listos() { kn get deploy bitacora-api -o jsonpath='{.status.readyReplicas}'; }
fila() { # t segundos | réplicas | CPU de la métrica del HPA
  echo "$1 | $(kn get hpa bitacora-api -o jsonpath='{.status.currentReplicas}/{.status.desiredReplicas} | {.status.currentMetrics[0].resource.current.averageUtilization}%')"; }
carga_on() { for i in 1 2 3 4; do kn run carga-$i --image=curlimages/curl:8.17.0 --restart=Never --overrides='{"spec":{"terminationGracePeriodSeconds":0}}' -- sh -c 'while true; do curl -s -o /dev/null -m 10 "http://bitacora-api/api/carga?ms=300"; done' >/dev/null; done; }
carga_off() { kn delete pod carga-1 carga-2 carga-3 carga-4 --now --ignore-not-found >/dev/null 2>&1; }
tabla() { # tabla fase segundos_max condición
  local fase=$1 max=$2 cond=$3 t=0
  while [ $t -le $max ]; do
    local f; f="$(fila $t)"; registrar "tabla_$fase: $f" >&2
    local actual; actual="$(kn get hpa bitacora-api -o jsonpath='{.status.currentReplicas}')"
    case "$cond" in sube) [ "$actual" = 6 ] && [ $t -ge 30 ] && { echo $t; return; };; baja) [ "$actual" = 2 ] && { echo $t; return; };; esac
    sleep 30; t=$((t+30))
  done; echo $t
}

k delete ns $NS --ignore-not-found --wait >/dev/null 2>&1
k create ns $NS >/dev/null
herramientas $NS

titulo "Paso 1: servidor de métricas"
nota "metrics-server: $(k -n kube-system get deploy metrics-server -o jsonpath='{.spec.template.spec.containers[0].image}'), args: $(k -n kube-system get deploy metrics-server -o jsonpath='{.spec.template.spec.containers[0].args}')"
afirmar "kubectl top nodes responde" k top nodes
registrar "paso1: metrics-server con --kubelet-insecure-tls (los kubelet de kind firman con su propia CA)"

titulo "Pasos 2 y 3: autoescalador y carga"
k apply -n $NS -f "$SOL/api.yaml" -f "$SOL/hpa.yaml" >/dev/null
kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
esperar_hasta 120 sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS top pods | grep -q bitacora-api"
igual "arranca con el mínimo, 2 copias" 2 "$(listos)"
carga_on
nota "t(s) | actuales/deseadas | CPU media sobre la petición"
tsub="$(tabla subida 300 sube)"
registrar "paso3_subida: 6 copias a los ${tsub}s"
afirmar "escaló hasta el máximo (6)" test "$(kn get hpa bitacora-api -o jsonpath='{.status.currentReplicas}')" = 6
sleep 20
carga_off
tbaja="$(tabla bajada_por_defecto 600 baja)"
registrar "paso3_bajada_por_defecto: 2 copias a los ${tbaja}s de parar la carga"
afirmar "volvió al mínimo (2)" test "$(kn get hpa bitacora-api -o jsonpath='{.status.currentReplicas}')" = 2

titulo "Paso 4: bajar más rápido"
k apply -n $NS -f "$SOL/hpa-rapido.yaml" >/dev/null
carga_on
tsub2="$(tabla subida2 300 sube)"; sleep 20
carga_off
tbaja2="$(tabla bajada_rapida 300 baja)"
registrar "paso4: con stabilizationWindowSeconds=30 bajó a las ${tbaja2}s (antes ${tbaja}s); subida ${tsub2}s"
afirmar "ahora baja más rápido" test "$tbaja2" -lt "$tbaja"

titulo "Paso 5: replicas fijas en el manifiesto contra el autoescalador"
carga_on
sleep 75
nota "durante la carga: $(fila 0)"
sed 's|^  progressDeadlineSeconds: 60|  replicas: 2\n  progressDeadlineSeconds: 60|' "$SOL/api.yaml" > "$SALIDAS/api-con-replicas.yaml"
tras="$(k apply -n $NS -f "$SALIDAS/api-con-replicas.yaml" -o jsonpath='{.items[?(@.kind=="Deployment")].spec.replicas}' 2>/dev/null)"
registrar "paso5: el apply con replicas: 2 durante la carga dejó spec.replicas=$tras (el autoescalador pedía 6); $(fila 0)"
igual "el apply pisó al autoescalador" 2 "$tras"
sleep 45
registrar "paso5: 45 s después el HPA volvió a subir: $(fila 0)"
k apply -n $NS -f "$SOL/api.yaml" >/dev/null
carga_off

titulo "Paso 6: despliegue malo con maxUnavailable: 0"
k delete -n $NS hpa bitacora-api >/dev/null
kn scale deploy bitacora-api --replicas=4 >/dev/null
kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
iniciar_medidor $NS http://bitacora-api/api/info; sleep 3
parche '[{"op":"replace","path":"/spec/template/spec/containers/0/env/1/value","value":"/proc/no-escribible"}]'
sleep 75
kn get pods -l app=bitacora-api --no-headers | awk '{print "     "$1, $2, $3}'
cond="$(kn get deploy bitacora-api -o jsonpath='{.status.conditions[?(@.type=="Progressing")].reason}')"
registrar "paso6: listos=$(listos) Progressing=$cond"
igual "la actualización queda estancada: ProgressDeadlineExceeded" ProgressDeadlineExceeded "$cond"
read -r total errores <<< "$(resultados_medidor $NS)"
registrar "paso6: peticiones=$total errores=$errores con 4 copias viejas atendiendo"
igual "cero errores" 0 "$errores"
kn rollout undo deploy/bitacora-api >/dev/null; kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
detener_medidor $NS

titulo "Paso 7: lo mismo con maxUnavailable: 50 %"
parche '[{"op":"replace","path":"/spec/strategy/rollingUpdate/maxUnavailable","value":"50%"}]'
kn rollout status deploy/bitacora-api --timeout=120s >/dev/null
iniciar_medidor $NS http://bitacora-api/api/info; sleep 3
parche '[{"op":"replace","path":"/spec/template/spec/containers/0/env/1/value","value":"/proc/no-escribible"}]'
sleep 40
registrar "paso7: con 50 % quedan listas $(listos) de 4 (con 0 quedaban 4)"
kn get pods -l app=bitacora-api --no-headers | awk '{print "     "$1, $2, $3}'
read -r total errores <<< "$(resultados_medidor $NS)"
registrar "paso7: peticiones=$total errores=$errores"
afirmar "la capacidad bajó por debajo de 4" test "$(listos)" -lt 4
kn rollout undo deploy/bitacora-api >/dev/null; kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
detener_medidor $NS
parche '[{"op":"replace","path":"/spec/strategy/rollingUpdate/maxUnavailable","value":0}]'

titulo "Paso 8: Recreate"
kn patch deploy bitacora-api --type=merge -p '{"spec":{"strategy":{"type":"Recreate","rollingUpdate":null}}}' >/dev/null
kn rollout status deploy/bitacora-api --timeout=120s >/dev/null
iniciar_medidor $NS http://bitacora-api/api/info; sleep 3
kn rollout restart deploy/bitacora-api >/dev/null; kn rollout status deploy/bitacora-api --timeout=240s >/dev/null; sleep 5
read -r total errores <<< "$(resultados_medidor $NS)"
corte="$(kn logs medidor | awk '$2!="200"{ if(!i)i=$1; f=$1 } END{ print (i? f-i+1 : 0) }')"
registrar "paso8_recreate: peticiones=$total errores=$errores corte_aprox_s=$corte"
afirmar "Recreate produce corte" test "$errores" -gt 0
detener_medidor $NS
k apply -n $NS -f "$SALIDAS/api-con-replicas.yaml" >/dev/null; kn scale deploy bitacora-api --replicas=4 >/dev/null
kn patch deploy bitacora-api --type=merge -p '{"spec":{"strategy":{"type":"RollingUpdate","rollingUpdate":{"maxUnavailable":0,"maxSurge":1}}}}' >/dev/null
kn rollout status deploy/bitacora-api --timeout=180s >/dev/null

titulo "Paso 9: vaciar un nodo con presupuesto de interrupción"
k apply -n $NS -f "$SOL/pdb.yaml" >/dev/null
nota "nodos de las copias: $(kn get pods -l app=bitacora-api -o jsonpath='{range .items[*]}{.spec.nodeName}{" "}{end}')"
iniciar_medidor $NS http://bitacora-api/api/info; sleep 3
# El nodo a vaciar tiene copias de la API pero NO el pod medidor (con --force el drain también borraría ese pod suelto y no mediría nada).
nodo_medidor="$(kn get pod medidor -o jsonpath='{.spec.nodeName}')"
nodo="$(kn get pods -l app=bitacora-api -o jsonpath='{range .items[*]}{.spec.nodeName}{"\n"}{end}' | grep -vx "$nodo_medidor" | head -1)"
[ -n "$nodo" ] || { nodo="$(kn get pods -l app=bitacora-api -o jsonpath='{.items[0].spec.nodeName}')"; kn delete pod medidor --now >/dev/null; mal "ninguna copia de la API fuera del nodo del medidor"; }
i=$(ahora_ms)
# --force: los pods sueltos (herramientas, medidor) no tienen controlador y, sin --force, abortarían el drain.
salida="$(k drain "$nodo" --ignore-daemonsets --delete-emptydir-data --force --timeout=180s 2>&1)"; rc_drain=$?
echo "$salida" | grep -E 'evict|drained|error' | sed 's/^/     /' | tail -12
igual "el primer drain terminó con código 0" 0 "$rc_drain"
igual "no queda ninguna copia de la API en el nodo vaciado" 0 "$(kn get pods -l app=bitacora-api --field-selector spec.nodeName=$nodo --no-headers | grep -vc Terminating)"
nota "drain con minAvailable=2: $(( $(ahora_ms) - i )) ms"
sleep 20
kn rollout status deploy/bitacora-api --timeout=120s >/dev/null
read -r total errores <<< "$(resultados_medidor $NS)"
registrar "paso9_drain_pdb2: nodo=$nodo (el medidor estaba en $nodo_medidor) peticiones=$total errores=$errores"
afirmar "el bucle midió de verdad (más de 100 peticiones)" test "$total" -gt 100
igual "vaciar el nodo no produjo errores" 0 "$errores"
detener_medidor $NS
k uncordon "$nodo" >/dev/null
kn rollout restart deploy/bitacora-api >/dev/null; kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
k apply -n $NS -f "$SOL/pdb-total.yaml" >/dev/null
sleep 3
nodo="$(kn get pods -l app=bitacora-api -o jsonpath='{.items[0].spec.nodeName}')"
afirmar "el nodo elegido tiene al menos una copia de la API" test "$(kn get pods -l app=bitacora-api --field-selector spec.nodeName=$nodo --no-headers | wc -l)" -ge 1
salida="$(k drain "$nodo" --ignore-daemonsets --delete-emptydir-data --force --timeout=25s 2>&1 || true)"
echo "$salida" | grep -E 'Cannot evict|error' | head -2 | sed 's/^/     /'
registrar "paso9_drain_pdb4: $(echo "$salida" | grep -m1 'Cannot evict')"
afirmar "con minAvailable=4 el drain se bloquea" grep -q "Cannot evict pod as it would violate the pod" <<< "$salida"
k uncordon "$nodo" >/dev/null
kn delete pdb bitacora-api >/dev/null

titulo "Paso 10: repartir las copias"
kn rollout restart deploy/bitacora-api >/dev/null; kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
reparto() { kn get pods -l app=bitacora-api -o jsonpath='{range .items[*]}{.spec.nodeName}{"\n"}{end}' | sort | uniq -c | awk '{printf "%s=%s ",$2,$1}'; }
registrar "paso10_antes: $(reparto)"
k apply -n $NS -f "$SOL/api-repartida.yaml" >/dev/null
kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
kn scale deploy bitacora-api --replicas=6 >/dev/null; kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
seis() { [ "$(kn get pods -l app=bitacora-api --no-headers | wc -l | tr -d ' ')" = 6 ] && [ "$(kn get pods -l app=bitacora-api --no-headers | grep -c Running)" = 6 ]; }
esperar_hasta 120 seis
registrar "paso10_despues: $(reparto)"
ndist="$(kn get pods -l app=bitacora-api -o jsonpath='{range .items[*]}{.spec.nodeName}{"\n"}{end}' | sort | uniq -c | awk '{print $1}' | sort -u | tr '\n' ' ')"
igual "6 copias repartidas 3 y 3" "3 " "$ndist"

titulo "Limpieza"
k get nodes --no-headers | awk '{print "     "$1, $2}'
k delete ns $NS --wait >/dev/null && ok "espacio de nombres borrado"
terminar "Kubernetes 07"
