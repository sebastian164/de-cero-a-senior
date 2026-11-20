#!/usr/bin/env bash
# Solución ejecutable del taller Kubernetes 01. Crea el clúster (nombre CLUSTER, por defecto «referencia») y lo baja al terminar.
# Puerto del port-forward: 50110 del equipo en lugar del 8080.
# El paso 8 espera más de 5 minutos a que Kubernetes desaloje el pod del nodo detenido: es opcional
# (ESPERA_LARGA=1 ./probar.sh). Sin ella se comprueba todo lo demás del paso.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
preparar_cluster
SOL="$AQUI"
NS=taller-k01
OUT="$SALIDAS/k8s-01.txt"; : > "$OUT"
kn() { k -n $NS "$@"; }

titulo "Kubernetes 01, paso 1: nodos y piezas del plano de control"
k get nodes
igual "tres nodos Ready" 3 "$(k get nodes --no-headers | awk '$2=="Ready"' | wc -l | tr -d ' ')"
k -n kube-system get pods -o wide --no-headers | awk '{printf "     %-55s %s\n", $1, $7}'
for pieza in etcd kube-apiserver kube-scheduler kube-controller-manager kube-proxy kindnet coredns; do
  afirmar "kube-system tiene $pieza" sh -c "KUBECONFIG=$KUBECONFIG kubectl --context kind-$CLUSTER -n kube-system get pods --no-headers | grep -q '^$pieza'"
done
echo "version: $(k version -o json | python3 -c 'import json,sys; print(json.load(sys.stdin)["serverVersion"]["gitVersion"])')" >> "$OUT"

titulo "Paso 2: la imagen está en los nodos"
for nodo in $(kind get nodes --name "$CLUSTER"); do
  afirmar "$nodo tiene bitacora-api:1.0.0" sh -c "docker exec $nodo crictl images | grep -E 'library/bitacora-api +1.0.0'"
done

titulo "Pasos 3 y 4: aplicar el pod, nodo, IP y port-forward"
k apply -f "$SOL/espacio-de-nombres.yaml" >/dev/null
kn delete pod bitacora-api --ignore-not-found --wait >/dev/null
k apply -f "$SOL/pod.yaml"
kn wait --for=condition=Ready pod/bitacora-api --timeout=90s >/dev/null
kn get pod bitacora-api -o wide
nodo1="$(kn get pod bitacora-api -o jsonpath='{.spec.nodeName}')"; ip1="$(kn get pod bitacora-api -o jsonpath='{.status.podIP}')"; uid1="$(kn get pod bitacora-api -o jsonpath='{.metadata.uid}')"
nota "nodo=$nodo1 ip=$ip1"
kn port-forward pod/bitacora-api 50110:8080 >/dev/null 2>&1 & PF=$!
esperar_hasta 15 curl -sf localhost:50110/api/info
info="$(curl -s localhost:50110/api/info)"; nota "port-forward: $info"
kill $PF 2>/dev/null; wait $PF 2>/dev/null
afirmar "/api/info responde por port-forward" sh -c "echo '$info' | grep -q '\"version\"'"

titulo "Paso 5: registros, usuario y host"
kn logs bitacora-api | head -3 | sed 's/^/     /'
igual "corre con el usuario 10001 de la imagen" "uid=10001(bitacora) gid=10001(bitacora) groups=10001(bitacora)" "$(kn exec bitacora-api -- id)"
igual "host de /api/info = nombre del pod (variable HOSTNAME que pone Kubernetes)" bitacora-api "$(echo "$info" | python3 -c 'import json,sys; print(json.load(sys.stdin)["host"])')"
igual "HOSTNAME dentro del contenedor" bitacora-api "$(kn exec bitacora-api -- sh -c 'echo $HOSTNAME')"

titulo "Paso 6: borrar el pod"
kn delete pod bitacora-api --wait >/dev/null
sleep 5
negar "nadie lo repone: no existe 5 s después" kn get pod bitacora-api
k apply -f "$SOL/pod.yaml" >/dev/null
kn wait --for=condition=Ready pod/bitacora-api --timeout=90s >/dev/null
ip2="$(kn get pod bitacora-api -o jsonpath='{.status.podIP}')"; uid2="$(kn get pod bitacora-api -o jsonpath='{.metadata.uid}')"
nota "IP antes=$ip1 después=$ip2 | uid antes=$uid1 después=$uid2"
negar "es otro pod (uid distinto)" test "$uid1" = "$uid2"
echo "borrar: ip_antes=$ip1 ip_despues=$ip2" >> "$OUT"

titulo "Paso 7: kill 1 dentro del contenedor"
kn exec bitacora-api -- kill 1
esperar_hasta 60 sh -c "KUBECONFIG=$KUBECONFIG kubectl --context kind-$CLUSTER -n $NS get pod bitacora-api -o jsonpath='{.status.containerStatuses[0].restartCount}' | grep -qx 1"
kn wait --for=condition=Ready pod/bitacora-api --timeout=60s >/dev/null
kn get pod bitacora-api
igual "RESTARTS sube a 1" 1 "$(kn get pod bitacora-api -o jsonpath='{.status.containerStatuses[0].restartCount}')"
igual "mismo pod (mismo uid)" "$uid2" "$(kn get pod bitacora-api -o jsonpath='{.metadata.uid}')"
igual "misma IP" "$ip2" "$(kn get pod bitacora-api -o jsonpath='{.status.podIP}')"
salida="$(kn get pod bitacora-api -o jsonpath='{.status.containerStatuses[0].lastState.terminated.exitCode} {.status.containerStatuses[0].lastState.terminated.reason}')"
nota "último estado del contenedor: $salida (143 = 128 + SIGTERM: kill sin número envía TERM y la JVM sale ordenadamente)"
kn logs bitacora-api --previous | tail -2 | sed 's/^/     /'
echo "kill1: lastState='$salida'" >> "$OUT"

titulo "Paso 8: detener el nodo del pod"
nodo="$(kn get pod bitacora-api -o jsonpath='{.spec.nodeName}')"
nota "el pod está en $nodo; docker stop $nodo"
inicio=$(date +%s)
docker stop "$nodo" >/dev/null
esperar_hasta 120 sh -c "KUBECONFIG=$KUBECONFIG kubectl --context kind-$CLUSTER get node $nodo --no-headers | grep -q NotReady"
t_notready=$(( $(date +%s) - inicio ))
nota "el nodo pasa a NotReady a los ${t_notready} s"
sleep 30
k get node "$nodo" --no-headers | sed 's/^/     /'
kn get pod -o wide --no-headers | sed 's/^/     /'
igual "30 s después de NotReady sigue habiendo un solo pod y en el mismo nodo: un pod suelto no se muda" "1 $nodo" "$(kn get pod --no-headers -o custom-columns=N:.spec.nodeName | sort -u | awk '{n++; v=$1} END {print n, v}')"
taints="$(k get node $nodo -o jsonpath='{range .spec.taints[*]}{.key}:{.effect} {end}')"
nota "taints del nodo: $taints"
afirmar "el controlador de nodos marcó el nodo como inalcanzable" sh -c "echo '$taints' | grep -q 'node.kubernetes.io/unreachable'"
toler="$(kn get pod bitacora-api -o jsonpath='{range .spec.tolerations[*]}{.key}={.tolerationSeconds} {end}')"
nota "tolerancias que añade Kubernetes al pod: $toler"
if [ "${ESPERA_LARGA:-0}" = 1 ]; then
# Se espera por encima de los 300 s de tolerationSeconds para ver qué pasa de verdad con el pod.
dt=0; until [ -n "$(kn get pod bitacora-api -o jsonpath='{.metadata.deletionTimestamp}' 2>/dev/null)" ] || ! kn get pod bitacora-api >/dev/null 2>&1; do sleep 5; dt=$((dt+5)); [ $dt -gt 400 ] && break; done
nota "el pod recibe deletionTimestamp (desalojo) $(( $(date +%s) - inicio )) s después de detener el nodo ($dt s tras la espera previa)"
sleep 20
estado="$(kn get pod bitacora-api --no-headers 2>&1 | awk '{print $1, $2, $3}')"
nota "estado del pod con el nodo todavía detenido: $estado"
afirmar "pasados los 300 s el pod queda Terminating (el kubelet del nodo muerto no puede confirmar el borrado)" sh -c "echo '$estado' | grep -q Terminating"
afirmar "ningún otro pod lo sustituye: un pod suelto no tiene quien lo recree" test "$(kn get pods --no-headers | wc -l | tr -d ' ')" = 1
else nota "(espera larga omitida: ESPERA_LARGA=1 comprueba que a los 300 s el pod queda Terminating)"; estado="omitido"; dt=0; fi
docker start "$nodo" >/dev/null
esperar_hasta 180 sh -c "KUBECONFIG=$KUBECONFIG kubectl --context kind-$CLUSTER get node $nodo --no-headers | grep -qw Ready"
igual "el nodo vuelve a Ready" Ready "$(k get node $nodo --no-headers | awk '{print $2}')"
if [ "${ESPERA_LARGA:-0}" = 1 ]; then
esperar_hasta 120 sh -c "! KUBECONFIG=$KUBECONFIG kubectl --context kind-$CLUSTER -n $NS get pod bitacora-api"
ok "al volver el nodo, el kubelet confirma el borrado y el pod desaparece del todo (nadie lo recrea)"
fi
k apply -f "$SOL/pod.yaml" >/dev/null
kn wait --for=condition=Ready pod/bitacora-api --timeout=120s >/dev/null && ok "aplicar de nuevo el archivo lo recrea" || mal "el pod no vuelve"
echo "nodo: notready_s=$t_notready tolerancias='$toler' desalojo_s=$dt estado_final='$estado'" >> "$OUT"

titulo "Paso 9: etiqueta que no existe"
k apply -f "$SOL/pod-etiqueta-inexistente.yaml" >/dev/null
esperar_hasta 60 sh -c "KUBECONFIG=$KUBECONFIG kubectl --context kind-$CLUSTER -n $NS describe pod bitacora-api | grep -q 'Failed to pull image'"
kn get pod bitacora-api --no-headers | sed 's/^/     /'
evento="$(kn describe pod bitacora-api | grep -m1 'Failed to pull image' | sed 's/^ *//' | cut -c1-200)"
nota "describe: $evento"
afirmar "describe nombra la imagen que no se pudo descargar" sh -c "echo '$evento' | grep -q 'bitacora-api:9.9.9'"
echo "etiqueta: $evento" >> "$OUT"

titulo "Paso 10: kubectl explain"
explica="$(k explain pod.spec.restartPolicy)"
echo "$explica" | sed -n '1,20p' | sed 's/^/     /'
for v in Always OnFailure Never; do afirmar "restartPolicy admite $v" sh -c "echo \"\$0\" | grep -q '\"$v\"'" "$explica"; done

titulo "Limpieza"
k delete -f "$SOL/espacio-de-nombres.yaml" --wait >/dev/null && ok "espacio de nombres borrado"
terminar "Kubernetes 01"
