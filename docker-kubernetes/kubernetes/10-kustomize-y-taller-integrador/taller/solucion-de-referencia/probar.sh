#!/usr/bin/env bash
# Solución ejecutable del taller integrador (Kubernetes 10): estructura, requisitos, reconstrucción desde cero y simulacro.
# El guion de esta carpeta (guion/construir.sh) crea su propio clúster, así que esta prueba no usa preparar_cluster.
# Herramientas: Docker, kind, kubectl, helm, kubeconform y kube-linter. Dura unos 25 minutos.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
SOL="$AQUI"
for h in helm kubeconform kube-linter; do command -v $h >/dev/null || { echo "Falta $h (ver README.md)"; exit 2; }; done
OUT="$SALIDAS/k8s-10.txt"; : > "$OUT"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/$PREFIJO-k10.XXXXXX")"
registrar() { echo "$1" >> "$OUT"; nota "$1"; }
NSP=bitacora-prod; NSD=bitacora-dev
H=bitacora.localhost:$PUERTO_ENTRADA
knp() { k -n $NSP "$@"; }

titulo "Parte 3 (5 y 6): destruir el clúster y reconstruirlo con el guion"
kind delete cluster --name "$CLUSTER" --kubeconfig "$KUBECONFIG" >/dev/null 2>&1; rm -f "$KUBECONFIG"
asegurar_bitacora api-1.0.0; asegurar_bitacora web-0.1.0
docker tag $PREFIJO-bitacora-api:1.0.0 $PREFIJO-bitacora-api:1.0.1
i=$(ahora_ms)
CLUSTER=$CLUSTER IMAGEN_API_1_0_0=$PREFIJO-bitacora-api:1.0.0 IMAGEN_API_1_0_1=$PREFIJO-bitacora-api:1.0.1 IMAGEN_WEB=$PREFIJO-bitacora-web:0.1.0 \
  "$SOL/guion/construir.sh" produccion > "$TMP/construir.log" 2>&1 && ok "construir.sh produccion terminó con 0" || { mal "construir.sh"; tail -15 "$TMP/construir.log"; }
registrar "reconstruccion_desde_cero: $(( ($(ahora_ms) - i)/1000 )) s (clúster, metrics-server, Envoy Gateway, imágenes y producción)"
for imagen in curlimages/curl:8.17.0; do cargar_imagen $imagen; done
esperar_hasta 120 sh -c "[ \"\$(kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NSP get deploy bitacora-api -o jsonpath='{.status.readyReplicas}')\" -ge 3 ]"

titulo "Parte 3 (7): validación previa sin clúster"
afirmar "kubeconform + kube-linter sobre las dos superposiciones" "$SOL/guion/validar.sh"

titulo "Parte 1 y 2: producción cumple los requisitos"
igual "kubectl apply -k de producción es idempotente" 0 "$(k apply -k "$SOL/superposiciones/produccion" >/dev/null 2>&1; echo $?)"
afirmar "ninguna imagen usa latest ni va sin etiqueta" sh -c "! kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NSP get deploy -o jsonpath='{..image}' | tr ' ' '\n' | grep -Ev ':[0-9]+\.[0-9]+\.[0-9]+$'"
registrar "imagenes: $(knp get deploy -o jsonpath='{..image}')"
n="$(knp get deploy bitacora-api -o jsonpath='{.status.readyReplicas}')"; afirmar "tres copias o más de la API listas ($n)" test "$n" -ge 3
nodos="$(knp get pods -l app=bitacora-api -o jsonpath='{range .items[*]}{.spec.nodeName}{"\n"}{end}' | sort -u | wc -l | tr -d ' ')"
afirmar "repartidas entre nodos ($nodos nodos)" test "$nodos" -ge 2
for sonda in startupProbe readinessProbe livenessProbe; do afirmar "API con $sonda" sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NSP get deploy bitacora-api -o jsonpath='{.spec.template.spec.containers[0].$sonda}' | grep -q httpGet"; done
afirmar "peticiones y límites declarados" sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NSP get deploy bitacora-api -o jsonpath='{.spec.template.spec.containers[0].resources}' | grep -q limits"
afirmar "apagado ordenado (preStop y ESPERA_APAGADO_MS)" sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NSP get deploy bitacora-api -o jsonpath='{.spec.template.spec.containers[0].lifecycle.preStop}' | grep -q sleep"
igual "Gateway con nombre de host" '["bitacora.localhost"]' "$(knp get httproute bitacora -o jsonpath='{.spec.hostnames}')"
afirmar "HPA y PDB" sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NSP get hpa/bitacora-api pdb/bitacora-api"
igual "restricted exigido" restricted "$(k get ns $NSP -o jsonpath='{.metadata.labels.pod-security\.kubernetes\.io/enforce}')"
afirmar "las políticas de red existen" sh -c "[ \$(kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NSP get netpol --no-headers | wc -l) -ge 3 ]"
herramientas $NSD >/dev/null 2>&1 || true
k -n $NSP run intruso --image=curlimages/curl:8.17.0 --restart=Never --overrides='{"spec":{"terminationGracePeriodSeconds":0,"securityContext":{"runAsNonRoot":true,"runAsUser":1000,"seccompProfile":{"type":"RuntimeDefault"}},"containers":[{"name":"intruso","image":"curlimages/curl:8.17.0","command":["sleep","100000"],"securityContext":{"allowPrivilegeEscalation":false,"capabilities":{"drop":["ALL"]}}}]}}' >/dev/null
k -n $NSP wait --for=condition=Ready pod/intruso --timeout=90s >/dev/null
igual "un pod ajeno no llega a la API" 000 "$(k -n $NSP exec intruso -- curl -s -o /dev/null -m 4 -w '%{http_code}' http://bitacora-api/api/info 2>/dev/null || true)"
k -n $NSP delete pod intruso --now >/dev/null
igual "la página carga por la entrada" 200 "$(codigo_http http://$H/)"
afirmar "es la página de Bitácora" sh -c "curl -s http://$H/ | grep -q '<title>Bitácora</title>'"
CLAVE="$(knp get secret bitacora-api-clave -o jsonpath='{.data.CLAVE_API}' | base64 -d)"
igual "sin la clave, POST /api/notas da 401" 401 "$(codigo_http -X POST --data 'sin clave' http://$H/api/notas)"
igual "con la clave del Secret generado, 201" 201 "$(codigo_http -X POST -H "X-Clave: $CLAVE" --data 'nota de produccion' http://$H/api/notas)"
igual "el mensaje es el de la superposición" "Bitácora en producción" "$(curl -s http://$H/api/info | python3 -c 'import sys,json; print(json.load(sys.stdin)["mensaje"])')"
notas_guardadas=5
for i in 1 2 3 4 5; do codigo_http -X POST -H "X-Clave: $CLAVE" --data "nota $i" http://$H/api/notas >/dev/null; done
# Cada copia guarda las notas que le tocaron: se cuentan las LISTAS distintas (su contenido, no su tamaño: un reparto 2/2/2 da tres listas
# distintas del mismo tamaño). Si todas las notas cayeran en una sola copia, se sigue guardando, como en el paso 1 del taller 06.
listas() { for i in $(seq 30); do curl -s http://$H/api/notas | python3 -c 'import sys,json; print(json.dumps(json.load(sys.stdin)["notas"], sort_keys=True))'; done | sort | uniq -c | sort -rn; }
for intento in 1 2 3 4 5 6 7 8; do
  distintas="$(listas | wc -l | tr -d ' ')"; [ "$distintas" -ge 2 ] && break
  notas_guardadas=$((notas_guardadas+1)); codigo_http -X POST -H "X-Clave: $CLAVE" --data "nota extra $intento" http://$H/api/notas >/dev/null
done
registrar "estado: las notas se guardan en emptyDir por copia (decisión documentada en DEFENSA.md): tras guardar $notas_guardadas notas, 30 lecturas por la entrada dan estas listas (lecturas, contenido): $(listas | sed 's/^ *//' | tr '\n' ';')"
afirmar "aparecen al menos 2 listas distintas (cada copia tiene la suya)" test "$distintas" -ge 2

titulo "Parte 1 (2 y 3): el ambiente de desarrollo y un cambio de configuración que se despliega solo"
# construir.sh sobre el clúster que ya existe (producción ya está desplegada): tiene que poder repetirse sin fallar.
CLUSTER=$CLUSTER IMAGEN_API_1_0_0=$PREFIJO-bitacora-api:1.0.0 IMAGEN_API_1_0_1=$PREFIJO-bitacora-api:1.0.1 IMAGEN_WEB=$PREFIJO-bitacora-web:0.1.0 \
  "$SOL/guion/construir.sh" desarrollo > "$TMP/construir-dev.log" 2>&1 && ok "construir.sh desarrollo sobre el clúster existente terminó con 0 (idempotente)" || { mal "construir.sh desarrollo"; tail -15 "$TMP/construir-dev.log"; }
k -n $NSD rollout status deploy/bitacora-api --timeout=180s >/dev/null; k -n $NSD rollout status deploy/bitacora-web --timeout=180s >/dev/null
svc_dev() { k -n envoy-gateway-system get svc -l gateway.envoyproxy.io/owning-gateway-name=entrada,gateway.envoyproxy.io/owning-gateway-namespace=$NSD -o jsonpath='{.items[0].metadata.name}' 2>/dev/null; }
esperar_hasta 120 sh -c "[ -n \"\$(kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n envoy-gateway-system get svc -l gateway.envoyproxy.io/owning-gateway-name=entrada,gateway.envoyproxy.io/owning-gateway-namespace=$NSD -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)\" ]"
svc="$(svc_dev)"
nota "Service del proxy de desarrollo: ${svc:-(no existe)}"
esperar_hasta 60 sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NSD get gateway entrada -o jsonpath='{.status.conditions[?(@.type==\"Programmed\")].status}' | grep -q True"
herramientas $NSD
dev() { dentro $NSD curl -s -m 5 -H 'Host: dev.bitacora.localhost' "http://$svc.envoy-gateway-system$1"; }
t_cm=$(date +%s)
esperar_hasta 120 sh -c "[ \"\$(kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NSD exec herramientas -- curl -s -o /dev/null -m 4 -w '%{http_code}' -H 'Host: dev.bitacora.localhost' http://$svc.envoy-gateway-system/api/info)\" = 200 ]"
msg_dev=""
for intento in $(seq 20); do   # el proxy del ambiente nuevo puede tardar unos segundos en aceptar conexiones
  msg_dev="$(dev /api/info 2>/dev/null | python3 -c 'import sys,json; print(json.load(sys.stdin)["mensaje"])' 2>/dev/null)" && [ -n "$msg_dev" ] && break
  sleep 3
done
igual "desarrollo responde con su propio mensaje" "Ambiente de desarrollo" "$msg_dev"
igual "una sola copia en desarrollo" 1 "$(k -n $NSD get deploy bitacora-api -o jsonpath='{.status.readyReplicas}')"
cm1="$(k -n $NSD get deploy bitacora-api -o jsonpath='{.spec.template.spec.containers[0].envFrom[0].configMapRef.name}')"
cp -R "$SOL" "$TMP/sol"
sed_i 's/MENSAJE=Ambiente de desarrollo/MENSAJE=Mensaje cambiado en la superposicion/' "$TMP/sol/superposiciones/desarrollo/kustomization.yaml"
k apply -k "$TMP/sol/superposiciones/desarrollo" >/dev/null
k -n $NSD rollout status deploy/bitacora-api --timeout=180s >/dev/null
cm2="$(k -n $NSD get deploy bitacora-api -o jsonpath='{.spec.template.spec.containers[0].envFrom[0].configMapRef.name}')"
registrar "configmapgenerator: $cm1 -> $cm2"
afirmar "el nombre del ConfigMap cambió (resumen del contenido)" test "$cm1" != "$cm2"
un_pod_dev() { [ "$(k -n $NSD get pods -l app=bitacora-api --no-headers | wc -l | tr -d ' ')" = 1 ]; }
esperar_hasta 120 un_pod_dev
t_cm=$(date +%s)
msg_svc() { dentro $NSD curl -s -m 5 http://bitacora-api/api/info | python3 -c 'import sys,json; print(json.load(sys.stdin)["mensaje"])' 2>/dev/null; }
esperar_hasta 60 sh -c "[ \"\$(kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NSD exec herramientas -- curl -s -m 4 http://bitacora-api/api/info | grep -c 'Mensaje cambiado')\" = 1 ]"
igual "y el Deployment se actualizó solo: el Service ya sirve el valor nuevo" "Mensaje cambiado en la superposicion" "$(msg_svc)"
msg_gw="$(dev /api/info | python3 -c 'import sys,json; print(json.load(sys.stdin)["mensaje"])' 2>/dev/null)"
registrar "configuracion: por el Gateway de desarrollo, tras el rollout: '$msg_gw' (el proxy puede tardar unos segundos en dejar de usar el pod viejo)"
afirmar "sin secretos en el repositorio de la solución" sh -c "! grep -rEi 'CLAVE_API[=:] *[0-9a-f]{16}' '$SOL'"
k delete ns $NSD --wait >/dev/null

titulo "Parte 4: simulacro con el bucle de medición contra bitacora.localhost:$PUERTO_ENTRADA"
BUCLE="$TMP/bucle.txt"; : > "$BUCLE"
( while :; do ini=$(date +%s); c=$(curl -s -o /dev/null -m 5 -w '%{http_code} %{exitcode}' http://$H/api/info); case "$c" in 000*) sleep 0.3; c=$(curl -s -o /dev/null -m 5 -w '%{http_code} %{exitcode}' http://$H/api/info);; esac; echo "$(date +%s) $c $ini" >> "$BUCLE"; sleep 0.2; done ) & BUCLE_PID=$!   # época de fin, código HTTP, código de salida de curl, época de inicio de la petición
errores_desde() { awk -v d="$1" '$1>=d && $2!="200"{e++; if(!i)i=$1; f=$1} $1>=d{t++} END{printf "%d %d %d", t, e+0, (e? f-i+1:0)}' "$BUCLE"; }
listas_api() { [ "$(knp get deploy bitacora-api -o jsonpath='{.status.readyReplicas}')" -ge 3 ] 2>/dev/null; }
prueba() { # prueba nombre esperado_cero(si|no) -> usa $T0
  read -r t e dur <<< "$(errores_desde $T0)"
  registrar "simulacro | $1 | peticiones=$t errores=$e duracion_errores_s=$dur codigos=$(awk -v d="$T0" '$1>=d && $2!="200"{c[$2]++} END{for(k in c) printf "%s:%d ", k, c[k]}' "$BUCLE") salida_curl=$(awk -v d="$T0" '$1>=d && $2!="200"{c[$3]++} END{for(k in c) printf "%s:%d ", k, c[k]}' "$BUCLE")"
  if [ "$2" = si ]; then igual "$1: cero errores" 0 "$e"; fi
  LAST_E=$e; LAST_D=$dur
}
sleep 4
T0=$(date +%s); knp delete $(knp get pods -l app=bitacora-api -o name | head -1) --wait=false >/dev/null; esperar_hasta 60 listas_api; sleep 8; prueba "borrar un pod de la API" si
T0=$(date +%s); knp delete pods -l app=bitacora-api --wait=false >/dev/null; sleep 3; esperar_hasta 120 listas_api; sleep 6
prueba "borrar todos los pods de la API a la vez (apagado ordenado)" no
registrar "simulacro | con apagado ordenado, los pods que terminan siguen atendiendo unos segundos: el borrado normal de todos a la vez casi no produce errores"
T0=$(date +%s); knp delete pods -l app=bitacora-api --force --grace-period=0 --wait=false >/dev/null 2>&1; sleep 3; esperar_hasta 120 listas_api; sleep 6
prueba "borrar todos los pods de la API de golpe (--force --grace-period=0, como un fallo)" no
# Con --force los pods desaparecen de golpe, pero el proxy puede enterarse antes de que llegue una petición: en 6 ejecuciones dio entre 0 y 4 errores.
# Lo que se afirma es lo que no varía: tras el fallo, el Deployment se recompone solo y los errores, si los hay, duran pocos segundos.
afirmar "se recuperó sola: 3 copias listas ($LAST_E errores, $LAST_D s)" listas_api
afirmar "los errores del fallo duran como mucho 10 s" test "$LAST_D" -le 10
T0=$(date +%s); knp rollout restart deploy/bitacora-api >/dev/null; knp rollout status deploy/bitacora-api --timeout=240s >/dev/null; sleep 8; prueba "reiniciar el Deployment" si
T0=$(date +%s); knp set env deploy/bitacora-api DIRECTORIO_DATOS=/proc/no-escribible >/dev/null; sleep 110
cond="$(knp get deploy bitacora-api -o jsonpath='{.status.conditions[?(@.type=="Progressing")].reason}')"
registrar "simulacro | version que nunca esta lista: Progressing=$cond listos=$(knp get deploy bitacora-api -o jsonpath='{.status.readyReplicas}')"
igual "la actualización se detiene" ProgressDeadlineExceeded "$cond"
prueba "desplegar una versión que nunca está lista" si
T0=$(date +%s); knp rollout undo deploy/bitacora-api >/dev/null; knp rollout status deploy/bitacora-api --timeout=240s >/dev/null; sleep 8; prueba "volver atrás" si
# ---- Reparto de la entrada ----
# topologySpreadConstraints solo actúa al programar un pod: tras un drain los dos proxies y los dos controladores de Envoy Gateway quedan en el
# mismo nodo (el otro estaba cordonado) y nada los vuelve a repartir al hacer uncordon. Quien opera lo hace a mano (o con un descheduler):
# expulsa una de las dos copias para que el planificador la coloque en el otro nodo. Antes de cada prueba se afirma que el reparto es bueno.
EG=envoy-gateway-system
PROXY="gateway.envoyproxy.io/owning-gateway-namespace=$NSP"; CTRL="control-plane=envoy-gateway"
nodos_de() { # etiqueta -> nodos distintos que alojan copias que no se están apagando
  k -n $EG get pods -l "$1" -o custom-columns=N:.spec.nodeName,D:.metadata.deletionTimestamp --no-headers | awk '$2=="<none>"{print $1}' | sort -u | wc -l | tr -d ' '; }
listos_de() { k -n $EG get pods -l "$1" -o custom-columns=D:.metadata.deletionTimestamp,R:.status.containerStatuses[*].ready --no-headers | awk '$1=="<none>" && $2 ~ /true/ && $2 !~ /false/' | wc -l | tr -d ' '; }
entrada_repartida() { [ "$(nodos_de $PROXY)" = 2 ] && [ "$(nodos_de $CTRL)" = 2 ] && [ "$(listos_de $PROXY)" = 2 ] && [ "$(listos_de $CTRL)" = 2 ]; }
restaurar_reparto() {
  local et dep pod
  for et in "$PROXY" "$CTRL"; do
    if [ "$(nodos_de $et)" -lt 2 ]; then
      pod="$(k -n $EG get pods -l "$et" -o name | tail -1)"
      nota "reparto roto en '$et': expulsando $pod para que el planificador la coloque en el otro nodo"
      k -n $EG delete "$pod" --wait=false >/dev/null
    fi
  done
  esperar_hasta 180 entrada_repartida
}
reparto_afirmado() { # texto
  nota "proxies en: $(k -n $EG get pods -l $PROXY -o jsonpath='{range .items[*]}{.spec.nodeName}{" "}{end}')| controladores en: $(k -n $EG get pods -l $CTRL -o jsonpath='{range .items[*]}{.spec.nodeName}{" "}{end}')"
  igual "$1: proxies en 2 nodos distintos" 2 "$(nodos_de $PROXY)"
  igual "$1: controladores de Envoy Gateway en 2 nodos distintos" 2 "$(nodos_de $CTRL)"
}
# Vacía un nodo con el bucle de medición y exige $3 (si: cero errores)
vaciar() { # nodo descripción exigir
  local nodo="$1" rc
  restaurar_reparto; reparto_afirmado "antes de vaciar $nodo"
  T0=$(date +%s); salida="$(k drain "$nodo" --ignore-daemonsets --delete-emptydir-data --force --timeout=240s 2>&1)"; rc=$?
  esperar_hasta 120 listas_api; sleep 8
  igual "el drain de $nodo terminó con código 0" 0 "$rc"
  igual "no queda ningún pod de la API en $nodo" 0 "$(knp get pods -l app=bitacora-api --field-selector spec.nodeName=$nodo --no-headers | grep -vc Terminating)"
  prueba "$2 ($nodo)" "$3"
  registrar "simulacro | $2: pods de la entrada tras el drain: proxies en $(k -n $EG get pods -l $PROXY -o jsonpath='{range .items[*]}{.spec.nodeName}{" "}{end}'), controladores en $(k -n $EG get pods -l $CTRL -o jsonpath='{range .items[*]}{.spec.nodeName}{" "}{end}')"
  k uncordon "$nodo" >/dev/null
}
nodo="$(k get nodes -l '!node-role.kubernetes.io/control-plane,!entrada' -o jsonpath='{.items[0].metadata.name}')"
nodo_entrada="$(k get nodes -l entrada=si -o jsonpath='{.items[0].metadata.name}')"
vaciar "$nodo" "vaciar un nodo de trabajo" si
knp rollout restart deploy/bitacora-api >/dev/null; knp rollout status deploy/bitacora-api --timeout=240s >/dev/null; sleep 6
# El nodo de entrada es el que tiene el mapeo del puerto del equipo (entrada=si). Con externalTrafficPolicy: Cluster ni el NodePort ni el proxy dependen de él.
for vez in $(seq "${REPETIR_ENTRADA:-3}"); do
  vaciar "$nodo_entrada" "vaciar el nodo de la entrada (vez $vez)" si
  knp rollout restart deploy/bitacora-api >/dev/null; knp rollout status deploy/bitacora-api --timeout=240s >/dev/null; sleep 6
done
# Detener de golpe un nodo de trabajo que NO es el del mapeo, con una copia del proxy y una del controlador en cada nodo.
restaurar_reparto; reparto_afirmado "antes de detener $nodo"
en_nodo="$(knp get pods -l app=bitacora-api -o jsonpath='{range .items[*]}{.spec.nodeName}{"\n"}{end}' | grep -c "^$nodo$")"
T0=$(date +%s); docker stop $nodo >/dev/null
tn=0; until [ "$(k get node $nodo -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}')" != True ]; do sleep 1; tn=$((tn+1)); [ $tn -gt 120 ] && break; done
TNR=$(date +%s)
# Recuperación sin intervención: el nodo sigue detenido hasta que el bucle lleva 15 s seguidos con 200 (contados desde que el nodo es NotReady).
racha_200() { awk -v n="$(date +%s)" -v u="$TNR" -v t0="$T0" '$1>=t0 && $2!="200"{u=$1} {ult=$1; c=$2} END{exit !(n-u>=15 && c=="200" && n-ult<=3)}' "$BUCLE"; }
esperar_hasta 240 racha_200 && recuperado=si || recuperado=no
TREC=$(date +%s)
igual "el nodo $nodo sigue detenido cuando termina la medición" false "$(docker inspect -f '{{.State.Running}}' $nodo)"
igual "los errores terminan solos, con el nodo aún detenido (15 s seguidos con 200)" si "$recuperado"
read -r t e dur <<< "$(errores_desde $T0)"
ultimo="$(awk -v d="$T0" '$1>=d && $2!="200"{f=$1} END{print (f? f-d : 0)}' "$BUCLE")"
prueba "detener un nodo de golpe ($nodo, con $en_nodo copias de la API)" no
registrar "simulacro | nodo detenido: NotReady a los ${tn} s; el último error llegó a los ${ultimo} s de detenerlo; 15 s seguidos con 200 a los $((TREC-T0)) s"
afirmar "Kubernetes tardó más de 30 s en reaccionar (node-monitor-grace-period 40 s)" test "$tn" -ge 30
afirmar "la recuperación sin intervención llegó en menos de 150 s ($ultimo s)" test "$ultimo" -lt 150
docker start $nodo >/dev/null
esperar_hasta 240 sh -c "[ \"\$(kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER get node $nodo -o jsonpath='{.status.conditions[?(@.type==\"Ready\")].status}')\" = True ]"
esperar_hasta 240 listas_api
sleep 5
# Pico de carga
carga() { for i in 1 2 3 4; do ( while [ -f "$TMP/carga" ]; do curl -s -o /dev/null -m 10 "http://$H/api/carga?ms=300"; done ) & done; }
touch "$TMP/carga"; carga
t=0; maxr=3
while [ $t -le 240 ]; do r="$(knp get hpa bitacora-api -o jsonpath='{.status.currentReplicas}')"; registrar "simulacro | pico t=${t}s replicas=$r cpu=$(knp get hpa bitacora-api -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}')%"; [ "$r" -gt "$maxr" ] && maxr=$r; [ "$r" = 6 ] && break; sleep 30; t=$((t+30)); done
rm -f "$TMP/carga"; sleep 12
afirmar "escaló hacia arriba (máximo $maxr copias)" test "$maxr" -gt 3
t=0
while [ $t -le 420 ]; do r="$(knp get hpa bitacora-api -o jsonpath='{.status.currentReplicas}')"; registrar "simulacro | bajada t=${t}s replicas=$r"; [ "$r" = 3 ] && break; sleep 30; t=$((t+30)); done
igual "volvió a 3 copias" 3 "$(knp get hpa bitacora-api -o jsonpath='{.status.currentReplicas}')"
kill $BUCLE_PID 2>/dev/null; wait $BUCLE_PID 2>/dev/null
cp "$BUCLE" "$SALIDAS/k8s-10-bucle.txt"   # para analizar cualquier error suelto: fin, código HTTP, código de curl, inicio

titulo "Limpieza"
rm -rf "$TMP"
k delete ns $NSP --wait >/dev/null && ok "espacios de nombres borrados"
terminar "Kubernetes 10"
