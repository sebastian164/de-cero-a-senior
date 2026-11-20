#!/usr/bin/env bash
# Solución ejecutable del taller Kubernetes 08: endurecer y cerrar la red (kindnet de kind v0.33).
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
preparar_cluster
instalar_envoy_gateway
SOL="$AQUI"
NS=bitacora-08
OUT="$SALIDAS/k8s-08.txt"; : > "$OUT"
kn() { k -n $NS "$@"; }
registrar() { echo "$1" >> "$OUT"; nota "$1"; }
H="bitacora.localhost:$PUERTO_ENTRADA"
# Desde un pod temporal (curl) del mismo espacio: ¿llega a la API?
intruso() { kn exec intruso -- curl -s -o /dev/null -m 4 -w '%{http_code}' http://bitacora-api/api/info 2>/dev/null || true; }
inventario() { # inventario etiqueta
  for p in api web; do
    pod="deploy/bitacora-$p"
    uid="$(kn exec $pod -- id -u 2>/dev/null)"
    esc="$(kn exec $pod -- sh -c 'touch /prueba-escritura 2>/dev/null && echo si || echo no' 2>/dev/null)"
    tok="$(kn exec $pod -- sh -c 'ls /var/run/secrets/kubernetes.io/serviceaccount/token 2>/dev/null && echo si || echo no' 2>/dev/null | tail -1)"
    registrar "inventario_$1: $p uid=$uid escribe_en_raiz=$esc credencial_api=$tok"
  done
}

k delete ns $NS --ignore-not-found --wait >/dev/null 2>&1
k create ns $NS >/dev/null

titulo "Paso 1: inventario (sin endurecer)"
k apply -f "$SOL/inicial.yaml" >/dev/null
for d in bitacora-api bitacora-web; do kn rollout status deploy/$d --timeout=180s >/dev/null; done
kn run intruso --image=curlimages/curl:8.17.0 --restart=Never --overrides='{"spec":{"terminationGracePeriodSeconds":0}}' -- sleep 100000 >/dev/null
kn wait --for=condition=Ready pod/intruso --timeout=90s >/dev/null
inventario antes
registrar "red_antes: pod ajeno -> API = $(intruso)"
igual "paso 7: el pod temporal llega a la API antes de cualquier política" 200 "$(intruso)"

titulo "Paso 2: aviso (warn) restricted"
kn label ns $NS pod-security.kubernetes.io/warn=restricted --overwrite >/dev/null 2>&1 || k label ns $NS pod-security.kubernetes.io/warn=restricted --overwrite >/dev/null
kn delete deploy bitacora-api bitacora-web >/dev/null     # sin cambios, apply no vuelve a enviar nada y no hay avisos: se recrean
avisos="$(k apply -f "$SOL/inicial.yaml" 2>&1 | grep -i warning | head -3)"
echo "$avisos" | sed 's/^/     /' | cut -c1-260
registrar "paso2_avisos: $(echo "$avisos" | head -1 | cut -c1-300)"
afirmar "hay avisos de restricted" test -n "$avisos"

titulo "Pasos 3 y 4: corregir la API y la web"
k apply -f "$SOL/aplicacion.yaml" 2>&1 | grep -ci warning | sed 's/^/     avisos tras corregir: /'
for d in bitacora-api bitacora-web; do kn rollout status deploy/$d --timeout=180s >/dev/null; done
kn logs deploy/bitacora-web --tail=15 | grep -E 'emerg|error|Permission|read-only' | head -3 | sed 's/^/     /'
igual "la API queda lista con solo lectura (/salud/listo)" 200 "$(kn exec deploy/bitacora-api -- wget -q -O /dev/null -S http://127.0.0.1:8080/salud/listo 2>&1 | awk '/HTTP\//{print $2}')"
igual "la web sirve la página" 200 "$(kn exec deploy/bitacora-web -- wget -q -O /dev/null -S http://127.0.0.1:8080/ 2>&1 | awk '/HTTP\//{print $2}')"
registrar "paso3: sin el volumen /datos, /salud/listo daria 503 porque la API no puede escribir su directorio (comprobado en la prueba de abajo)"
cp "$SOL/aplicacion.yaml" "$SALIDAS/aplicacion-sin-datos.yaml"
sed_i 's|            - {name: DIRECTORIO_DATOS, value: /datos}.*||; s|            - {name: datos, mountPath: /datos}||' "$SALIDAS/aplicacion-sin-datos.yaml"
sed_i 's|^          env:$|          env: []|' "$SALIDAS/aplicacion-sin-datos.yaml"
k apply -f "$SALIDAS/aplicacion-sin-datos.yaml" >/dev/null 2>&1
sleep 25
sin="$(kn get pods -l app=bitacora-api --no-headers | awk '{print $2}' | sort | uniq -c | tr -s ' ' | tr '\n' ';')"
registrar "paso3_sin_volumen_de_datos: pods de la API (listos) = $sin"
kn logs -l app=bitacora-api --tail=3 | grep -m1 ERROR | sed 's/^/     /'
k apply -f "$SOL/aplicacion.yaml" >/dev/null
kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
inventario despues
afirmar "API sin credencial de la API de Kubernetes" sh -c "! kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS exec deploy/bitacora-api -- ls /var/run/secrets/kubernetes.io/serviceaccount 2>/dev/null"
afirmar "web sin credencial de la API de Kubernetes" sh -c "! kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS exec deploy/bitacora-web -- ls /var/run/secrets/kubernetes.io/serviceaccount 2>/dev/null"

titulo "Paso 5: enforce y un pod privilegiado"
k label ns $NS pod-security.kubernetes.io/enforce=restricted --overwrite >/dev/null
msg="$(k apply -f "$SOL/privilegiado.yaml" 2>&1 || true)"
registrar "paso5_rechazo: $(echo "$msg" | tr '\n' ' ' | cut -c1-420)"
afirmar "el pod privilegiado se rechaza" sh -c "echo '$msg' | grep -q 'violates PodSecurity'"

titulo "Paso 6: un Deployment que no cumple no falla al aplicarlo"
k apply -f "$SOL/inicial.yaml" 2>&1 | grep -E 'deployment|Warning' | head -2 | cut -c1-200 | sed 's/^/     /'
sleep 5
kn get rs -l app=bitacora-api --no-headers | awk '{print "     rs", $1, "deseadas="$2, "listas="$4}'
ev="$(kn get events --field-selector reason=FailedCreate -o jsonpath='{.items[-1:].message}' | cut -c1-300)"
registrar "paso6_evento_replicaset: $ev"
afirmar "el error aparece en los eventos del ReplicaSet (FailedCreate)" test -n "$ev"
k apply -f "$SOL/aplicacion.yaml" >/dev/null
for d in bitacora-api bitacora-web; do kn rollout status deploy/$d --timeout=180s >/dev/null; done
kn delete rs -l app=bitacora-api --field-selector status.replicas=0 >/dev/null 2>&1 || true

titulo "Entrada por Gateway y aplicación completa"
k apply -f "$SOL/gateway.yaml" >/dev/null
esperar_hasta 90 test "$(codigo_http http://$H/)" = 200
igual "la página carga por la entrada" 200 "$(codigo_http http://$H/)"
igual "guarda notas" 201 "$(codigo_http -X POST --data 'nota endurecida' http://$H/api/notas)"
# Cada copia de la API guarda sus notas en su propio disco (sesión 06): la lista correcta solo sale de la copia que recibió el POST.
afirmar "y las lista (en alguna de 20 lecturas: cada copia tiene su propia lista)" sh -c "for i in \$(seq 20); do curl -s http://$H/api/notas | grep -q 'nota endurecida' && exit 0; done; exit 1"

titulo "Paso 8: cerrar toda la entrada"
k apply -f "$SOL/politica-denegar-entrada.yaml" >/dev/null
sleep 6
c="$(intruso)"
registrar "paso8: pod ajeno -> API = $c ; la entrada por el Gateway = $(codigo_http http://$H/)"
if [ "$c" = 000 ]; then ok "el complemento de red (kindnet) aplica las políticas: el pod temporal ya no llega"
else mal "las políticas NO se aplican (código $c): hay que cambiar de complemento"; fi
negar "web tampoco llega a la API mientras todo está cerrado" test "$(kn exec deploy/bitacora-web -- wget -q -T 3 -O /dev/null -S http://bitacora-api/api/info 2>&1 | awk '/HTTP\//{print $2}')" = 200

titulo "Paso 9: abrir solo lo necesario"
k apply -f "$SOL/politicas-permitir.yaml" >/dev/null
sleep 6
igual "web -> API (por el proxy de /api/)" 200 "$(kn exec deploy/bitacora-web -- wget -q -T 3 -O /dev/null -S http://bitacora-api/api/info 2>&1 | awk '/HTTP\//{print $2}')"
igual "la entrada llega a la página" 200 "$(codigo_http http://$H/)"
igual "la entrada llega a /api" 200 "$(codigo_http http://$H/api/info)"
igual "el pod ajeno sigue sin llegar a la API" 000 "$(intruso)"
c2="$(kn exec intruso -- curl -s -o /dev/null -m 4 -w '%{http_code}' http://bitacora-web/ 2>/dev/null || true)"
igual "el pod ajeno sigue sin llegar a la web" 000 "$c2"
registrar "paso9: web->api=200 entrada=200 ajeno->api=$(intruso) ajeno->web=$c2"

titulo "Paso 10: cerrar la salida de la API"
k apply -f "$SOL/politica-salida-api.yaml" >/dev/null; sleep 6
dns="$(kn exec deploy/bitacora-api -- sh -c 'nslookup bitacora-web.'$NS'.svc.cluster.local >/dev/null 2>&1 && echo resuelve || echo falla')"
igual "con la salida cerrada la API no resuelve nombres" falla "$dns"
igual "y sigue sirviendo peticiones (las respuestas no necesitan regla de salida)" 200 "$(codigo_http http://$H/api/info)"
k apply -f "$SOL/politica-salida-api-dns.yaml" >/dev/null; sleep 6
dns="$(kn exec deploy/bitacora-api -- sh -c 'nslookup bitacora-web.'$NS'.svc.cluster.local >/dev/null 2>&1 && echo resuelve || echo falla')"
igual "con la regla estrecha (solo kube-dns:53) resuelve" resuelve "$dns"
kn exec deploy/bitacora-api -- sh -c 'nc -z -w 3 bitacora-web 80 && echo abierto || echo cerrado' | sed 's/^/     API -> web:80: /'
igual "pero no puede conectar a la web" cerrado "$(kn exec deploy/bitacora-api -- sh -c 'nc -z -w 3 bitacora-web 80 && echo abierto || echo cerrado')"
registrar "paso10: sin salida: dns=falla, peticiones entrantes siguen 200; con regla a kube-dns:53 resuelve y sigue sin poder abrir conexiones a otros pods"

titulo "Paso 11: permisos"
k apply -f "$SOL/rbac.yaml" >/dev/null
SA="system:serviceaccount:$NS:lector-de-configuracion"
for v in "get configmaps" "list configmaps" "get secrets" "delete configmaps" "get pods"; do
  r="$(k auth can-i $v -n $NS --as=$SA)"; registrar "paso11: can-i $v -> $r"
done
igual "puede leer configmaps" yes "$(k auth can-i get configmaps -n $NS --as=$SA)"
igual "no puede leer secrets" no "$(k auth can-i get secrets -n $NS --as=$SA)"
igual "no puede borrar configmaps" no "$(k auth can-i delete configmaps -n $NS --as=$SA)"
igual "no puede leer configmaps de otro espacio" no "$(k auth can-i get configmaps -n default --as=$SA)"

titulo "Limpieza"
k delete ns $NS --wait >/dev/null && ok "espacio de nombres borrado"
terminar "Kubernetes 08"
