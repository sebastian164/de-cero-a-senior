#!/usr/bin/env bash
# Solución ejecutable del taller Kubernetes 03. Puerto de port-forward: 50130.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
preparar_cluster
SOL="$AQUI"
OUT="$SALIDAS/k8s-03.txt"; : > "$OUT"
kubectl config use-context "kind-$CLUSTER" >/dev/null
info() { dentro "$1" curl -s -m 5 --retry 8 --retry-all-errors --retry-delay 1 "http://bitacora-api/api/info"; }
campo() { python3 -c "import json,sys; print(json.load(sys.stdin)['$1'])"; }

k delete ns bitacora bitacora-pruebas --ignore-not-found --wait >/dev/null 2>&1

titulo "Kubernetes 03, pasos 1, 2 y 7: dos ambientes con los mismos archivos"
for ns in bitacora bitacora-pruebas; do
  k apply -f "$SOL/ambientes/$ns/configuracion.yaml" >/dev/null
  "$SOL/crear-secreto.sh" $ns >/dev/null
  k apply -n $ns -f "$SOL/api.yaml" -f "$SOL/web.yaml" >/dev/null
done
for ns in bitacora bitacora-pruebas; do
  k -n $ns rollout status deploy/bitacora-api --timeout=120s >/dev/null
  k -n $ns rollout status deploy/bitacora-web --timeout=120s >/dev/null
  herramientas $ns
  nota "$ns: $(info $ns)"
done
igual "bitacora: MENSAJE" "Hola desde la bitácora" "$(info bitacora | campo mensaje)"
igual "bitacora-pruebas: MENSAJE" "Bitácora de pruebas" "$(info bitacora-pruebas | campo mensaje)"
afirmar "api.yaml y web.yaml no fijan espacio de nombres" sh -c "! grep -q 'namespace:' '$SOL/api.yaml' '$SOL/web.yaml'"
afirmar "los Deployment no contienen MENSAJE, VERSION, DESTINO_API ni valores de clave" sh -c "! grep -Eq '(MENSAJE|VERSION|DESTINO_API|value: )' '$SOL/api.yaml' '$SOL/web.yaml'"

titulo "Paso 2: la clave"
clave="$(k -n bitacora get secret bitacora-api-clave -o jsonpath='{.data.CLAVE_API}' | base64 -d)"
clave_p="$(k -n bitacora-pruebas get secret bitacora-api-clave -o jsonpath='{.data.CLAVE_API}' | base64 -d)"
negar "cada ambiente tiene su propia clave" test "$clave" = "$clave_p"
igual "POST sin cabecera" 401 "$(dentro bitacora curl -s -o /dev/null -w '%{http_code}' -X POST --data 'sin clave' http://bitacora-api/api/notas)"
igual "POST con X-Clave" 201 "$(dentro bitacora curl -s -o /dev/null -w '%{http_code}' -X POST -H "X-Clave: $clave" --data 'con clave' http://bitacora-api/api/notas)"
igual "la clave de pruebas no sirve en el otro ambiente" 401 "$(dentro bitacora curl -s -o /dev/null -w '%{http_code}' -X POST -H "X-Clave: $clave_p" --data 'x' http://bitacora-api/api/notas)"
k -n bitacora port-forward svc/bitacora-web 50130:80 >/dev/null 2>&1 & PF=$!
esperar_hasta 15 curl -sf localhost:50130/
igual "la web funciona con DESTINO_API del ConfigMap" 200 "$(codigo_http localhost:50130/api/info)"
kill $PF; wait $PF 2>/dev/null

titulo "Criterio: el valor de la clave no está en ningún archivo"
negar "grep -r del valor de bitacora en la solución" grep -rqF "$clave" "$SOL"
negar "grep -r del valor de bitacora-pruebas en la solución" grep -rqF "$clave_p" "$SOL"

titulo "Paso 3: leer el Secret"
nota "kubectl get secret -o jsonpath='{.data.CLAVE_API}' | base64 -d -> ${#clave} caracteres en claro (se muestran los 6 primeros: ${clave:0:6}…)"
afirmar "cualquiera con permiso de leer Secrets la obtiene en claro" test "${#clave}" -eq 48

titulo "Paso 4: cambiar MENSAJE en el ConfigMap (variables de entorno)"
k -n bitacora create configmap bitacora-api-config --from-literal=MENSAJE="Mensaje cambiado" --from-literal=VERSION=1.0.0 --dry-run=client -o yaml | k apply -f - >/dev/null
sleep 20
igual "20 s después del apply, /api/info sigue con el mensaje viejo" "Hola desde la bitácora" "$(info bitacora | campo mensaje)"
i=$(ahora_ms)
k -n bitacora rollout restart deploy/bitacora-api >/dev/null
k -n bitacora rollout status deploy/bitacora-api --timeout=120s >/dev/null
t_env=$(( $(ahora_ms) - i ))
igual "tras rollout restart el mensaje cambia" "Mensaje cambiado" "$(info bitacora | campo mensaje)"
nota "con variables de entorno: sin cambio a los 20 s; con rollout restart, ${t_env} ms"
echo "entorno: sin_cambio_a_20s rollout_restart=${t_env}ms" >> "$OUT"

titulo "Paso 5: la misma clave como archivo montado"
k -n bitacora apply -f "$SOL/pruebas/montaje.yaml" >/dev/null
k -n bitacora wait --for=condition=Ready pod/prueba-montaje --timeout=60s >/dev/null
igual "dentro del pod, /config/MENSAJE" "Mensaje cambiado" "$(k -n bitacora exec prueba-montaje -- cat /config/MENSAJE)"
k -n bitacora create configmap bitacora-api-config --from-literal=MENSAJE="Mensaje por archivo" --from-literal=VERSION=1.0.0 --dry-run=client -o yaml | k apply -f - >/dev/null
i=$(date +%s)
esperar_hasta 180 sh -c "kubectl -n bitacora exec prueba-montaje -- cat /config/MENSAJE | grep -q 'por archivo'"
t_arch=$(( $(date +%s) - i ))
igual "el archivo se actualiza solo" "Mensaje por archivo" "$(k -n bitacora exec prueba-montaje -- cat /config/MENSAJE)"
nota "el archivo cambió a los ${t_arch} s sin reiniciar el pod (restarts: $(k -n bitacora get pod prueba-montaje -o jsonpath='{.status.containerStatuses[0].restartCount}'))"
nota "$(k -n bitacora exec prueba-montaje -- ls -la /config | tail -3 | tr '\n' ' ')"
echo "archivo: actualizado_a_los=${t_arch}s" >> "$OUT"

titulo "Paso 6: clave inexistente"
k -n bitacora apply -f "$SOL/pruebas/clave-inexistente.yaml" >/dev/null
esperar_hasta 60 sh -c "kubectl -n bitacora get pod clave-inexistente --no-headers | grep -q CreateContainerConfigError"
k -n bitacora get pod clave-inexistente --no-headers | sed 's/^/     /'
evento="$(k -n bitacora get events --field-selector involvedObject.name=clave-inexistente,reason=Failed -o jsonpath='{.items[0].message}')"
nota "evento: $evento"
igual "mensaje exacto del evento" "Error: couldn't find key MENSAJE_QUE_NO_EXISTE in ConfigMap bitacora/bitacora-api-config" "$evento"
echo "clave_inexistente: $evento" >> "$OUT"

titulo "Paso 8: ConfigMap inmutable"
k -n bitacora apply -f "$SOL/pruebas/inmutable.yaml" >/dev/null
error="$(k -n bitacora patch configmap bitacora-inmutable -p '{"data":{"MENSAJE":"otro"}}' 2>&1)"
nota "$error"
afirmar "el servidor de la API rechaza el cambio" sh -c "echo \"\$0\" | grep -q 'field is immutable when \`immutable\` is set'" "$error"
afirmar "solo se puede borrar y crear de nuevo" sh -c "kubectl -n bitacora delete configmap bitacora-inmutable && kubectl -n bitacora apply -f '$SOL/pruebas/inmutable.yaml'"
echo "inmutable: $error" >> "$OUT"

titulo "Limpieza"
k delete ns bitacora bitacora-pruebas --wait >/dev/null && ok "espacios de nombres borrados"
terminar "Kubernetes 03"
