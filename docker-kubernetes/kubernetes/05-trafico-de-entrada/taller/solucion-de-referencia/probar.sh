#!/usr/bin/env bash
# Solución ejecutable del taller Kubernetes 05 (Envoy Gateway, NodePort 30080 -> 127.0.0.1:$PUERTO_ENTRADA).
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
preparar_cluster
instalar_envoy_gateway
SOL="$AQUI"
NS=bitacora
OUT="$SALIDAS/k8s-05.txt"; : > "$OUT"
kn() { k -n $NS "$@"; }
H="bitacora.localhost:$PUERTO_ENTRADA"
registrar() { echo "$1" >> "$OUT"; nota "$1"; }
cuenta() { # cuenta N URL [cabecera]: devuelve "1.0.0=a 2.0.0=b"
  local n=$1; shift
  for i in $(seq $n); do curl -s -m 5 "$@"; echo; done | python3 -c '
import sys,json,collections
c=collections.Counter()
for l in sys.stdin:
    try: c[json.loads(l)["version"]]+=1
    except Exception: c["error"]+=1
print(" ".join("%s=%d"%(k,v) for k,v in sorted(c.items())))'
}
condicion() { kn get "$1" "$2" -o json | python3 -c '
import sys,json
d=json.load(sys.stdin)
def c(l): return " ".join("%s=%s(%s)"%(x["type"],x["status"],x.get("reason","")) for x in l)
if "parents" in d.get("status",{}): print(c(d["status"]["parents"][0]["conditions"]))
else: print(c(d["status"]["conditions"]))'; }

k delete ns $NS --ignore-not-found --wait >/dev/null 2>&1

titulo "Pasos 1 y 2: controlador y llegada desde el navegador"
nota "Envoy Gateway $(k -n envoy-gateway-system get deploy envoy-gateway -o jsonpath='{.spec.template.spec.containers[0].image}')"
k get gatewayclass | sed 's/^/     /'
igual "GatewayClass eg aceptada" True "$(k get gatewayclass eg -o jsonpath='{.status.conditions[?(@.type=="Accepted")].status}')"
registrar "controlador: $(k -n envoy-gateway-system get deploy envoy-gateway -o jsonpath='{.spec.template.spec.containers[0].image}') gatewayclass=eg nodeport=30080->$PUERTO_ENTRADA"

titulo "Paso 3: Gateway y HTTPRoute para bitacora.localhost"
k apply -f "$SOL/aplicacion.yaml" >/dev/null
for d in bitacora-api bitacora-web; do kn rollout status deploy/$d --timeout=180s >/dev/null; done
k apply -f "$SOL/gateway.yaml" -f "$SOL/ruta-basica.yaml" >/dev/null
esperar_hasta 90 test "$(codigo_http http://$H/)" = 200
igual "la página carga por bitacora.localhost:$PUERTO_ENTRADA" 200 "$(codigo_http http://$H/)"
afirmar "es la página de Bitácora" sh -c "curl -s http://$H/ | grep -q '<title>Bitácora</title>'"
igual "la API responde por /api/info" 200 "$(codigo_http http://$H/api/info)"
igual "guarda notas (POST /api/notas)" 201 "$(codigo_http -X POST --data 'nota por la entrada' http://$H/api/notas)"

titulo "Paso 4: condiciones, y una ruta rota a propósito"
nota "Gateway:   $(condicion gateway entrada)"
nota "HTTPRoute: $(condicion httproute bitacora)"
afirmar "Gateway Accepted y Programmed" sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS get gateway entrada -o jsonpath='{.status.conditions[*].type}={.status.conditions[*].status}' | grep -q 'Accepted Programmed=True True'"
k apply -f "$SOL/ruta-rota.yaml" >/dev/null; sleep 4
rota="$(condicion httproute bitacora)"
registrar "ruta_rota: $rota ; mensaje: $(kn get httproute bitacora -o jsonpath='{.status.parents[0].conditions[?(@.type=="ResolvedRefs")].message}')"
afirmar "ResolvedRefs pasa a False (BackendNotFound)" sh -c "echo '$rota' | grep -q 'ResolvedRefs=False(BackendNotFound)'"
nota "la orden apply no protestó; el usuario ve /api/info -> $(codigo_http http://$H/api/info)"
k apply -f "$SOL/ruta-basica.yaml" >/dev/null; sleep 3
igual "restaurada la ruta, /api/info vuelve a 200" 200 "$(codigo_http http://$H/api/info)"

titulo "Paso 5: /salud no se alcanza desde fuera"
for r in /salud/vivo /salud/listo; do nota "GET $r -> $(codigo_http http://$H$r), cuerpo: $(curl -s http://$H$r | head -c 60 | tr '\n' ' ')"; done
afirmar "/salud/vivo no devuelve el texto 'vivo' de la API" sh -c "! curl -s http://$H/salud/vivo | grep -qx vivo"
igual "/salud/vivo da 404 (lo atiende nginx, que no tiene esa ruta)" 404 "$(codigo_http http://$H/salud/vivo)"
registrar "salud_externa: vivo=$(codigo_http http://$H/salud/vivo) listo=$(codigo_http http://$H/salud/listo)"

titulo "Paso 6: canario del 10 %"
k apply -f "$SOL/api-v2.yaml" >/dev/null
kn rollout status deploy/bitacora-api-v2 --timeout=180s >/dev/null
sed 's/PESO_V1/90/; s/PESO_V2/10/' "$SOL/ruta-canario.yaml" | k apply -f - >/dev/null
sleep 4
afirmar "HTTPRoute aceptada" sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS get httproute bitacora -o jsonpath='{.status.parents[0].conditions[?(@.type==\"Accepted\")].status}' | grep -q True"
r="$(cuenta 200 http://$H/api/info)"
registrar "canario_10: 200 peticiones -> $r"
v2="$(echo "$r" | tr ' ' '\n' | sed -n 's/^2.0.0=//p')"
afirmar "la versión nueva recibe una parte pequeña pero no cero ($v2 de 200)" test "${v2:-0}" -ge 3 -a "${v2:-0}" -le 45

titulo "Paso 7: cabecera X-Probador"
r="$(cuenta 50 -H 'X-Probador: si' http://$H/api/info)"
registrar "cabecera: 50 peticiones con X-Probador: si -> $r"
igual "todas a la 2.0.0" "2.0.0=50" "$r"

titulo "Paso 10: redirección /notas -> /"
cab="$(curl -s -o /dev/null -D - http://$H/notas | tr -d '\r' | grep -iE '^(HTTP|location)' | tr '\n' ' ')"
registrar "redireccion: $cab"
igual "código 302" 302 "$(codigo_http http://$H/notas)"
afirmar "Location apunta a /" sh -c "curl -s -o /dev/null -D - http://$H/notas | tr -d '\r' | grep -i '^location:' | grep -q '://bitacora.localhost:$PUERTO_ENTRADA/\$'"
igual "/api/notas no se redirige" 200 "$(codigo_http http://$H/api/notas)"

titulo "Paso 8: 50 %, 100 % y retirar la versión anterior, con el bucle de medición"
svc="$(k -n envoy-gateway-system get svc -l gateway.envoyproxy.io/owning-gateway-name=entrada -o jsonpath='{.items[0].metadata.name}')"
herramientas $NS
iniciar_medidor $NS "-H 'Host: bitacora.localhost' http://$svc.envoy-gateway-system/api/info"
sleep 3
rm -f "$SALIDAS/k8s-05-fin"
# Medición también desde el equipo, por el nombre público (bitacora.localhost:$PUERTO_ENTRADA), durante todo el paso.
( while [ ! -f "$SALIDAS/k8s-05-fin" ]; do echo "$(curl -s -o /dev/null -m 3 -w '%{http_code}' http://$H/api/info)"; sleep 0.2; done > "$SALIDAS/k8s-05-host.txt" ) &
MEDIDOR_HOST=$!
for par in "50 50" "0 100"; do
  set -- $par
  sed "s/PESO_V1/$1/; s/PESO_V2/$2/" "$SOL/ruta-canario.yaml" | k apply -f - >/dev/null
  sleep 8
  registrar "paso8_pesos_$1_$2: $(cuenta 40 http://$H/api/info)"
done
# Nombre estable: el Service bitacora-api pasa a seleccionar los pods de la 2.0.0 y la ruta vuelve a una sola regla.
k apply -f "$SOL/servicio-final.yaml" >/dev/null
k apply -f "$SOL/ruta-final.yaml" >/dev/null; sleep 6
kn delete deploy bitacora-api --wait >/dev/null; kn delete svc bitacora-api-v2 >/dev/null
sleep 8
read -r total errores <<< "$(resultados_medidor $NS)"
detener_medidor $NS
registrar "paso8_medicion: peticiones=$total errores=$errores"
igual "ni un error en el bucle durante todo el paso a la 2.0.0" 0 "$errores"
touch "$SALIDAS/k8s-05-fin"; wait $MEDIDOR_HOST; rm -f "$SALIDAS/k8s-05-fin"
h_total="$(wc -l < "$SALIDAS/k8s-05-host.txt" | tr -d ' ')"; h_err="$(grep -vc '^200$' "$SALIDAS/k8s-05-host.txt")"
registrar "paso8_medicion_desde_el_equipo: peticiones=$h_total errores=$h_err"
igual "ni un error midiendo por bitacora.localhost:$PUERTO_ENTRADA (desde el equipo)" 0 "$h_err"
# Espera a que el proxy de entrada termine de actualizar sus destinos antes de medir el estado final.
for _ in $(seq 60); do [ "$(cuenta 10 http://$H/api/info)" = "2.0.0=10" ] && break; sleep 1; done
final="$(cuenta 30 http://$H/api/info)"
registrar "paso8_final (por bitacora.localhost:$PUERTO_ENTRADA): $final"
igual "las 30 peticiones desde el equipo van a la 2.0.0, sin errores" "2.0.0=30" "$final"
nota "la web resuelve bitacora-api al arrancar: se reinicia para comprobar que el estado final no depende de un nombre que ya no existe"
rm -f "$SALIDAS/k8s-05-fin2"
( while [ ! -f "$SALIDAS/k8s-05-fin2" ]; do curl -s -o /dev/null -m 3 -w '%{http_code}\n' http://$H/; sleep 0.2; done > "$SALIDAS/k8s-05-web.txt" ) &
MEDIDOR_WEB=$!
kn rollout restart deploy/bitacora-web >/dev/null
afirmar "la web se reinicia correctamente tras retirar la v1" kn rollout status deploy/bitacora-web --timeout=120s
sleep 8
touch "$SALIDAS/k8s-05-fin2"; wait $MEDIDOR_WEB; rm -f "$SALIDAS/k8s-05-fin2"
registrar "reinicio_de_la_web: $(sort "$SALIDAS/k8s-05-web.txt" | uniq -c | tr -s ' ' | tr '\n' ';') (la web no tiene preStop: puede haber algún 503 mientras el proxy actualiza sus destinos)"
estables=0; for i in $(seq 20); do [ "$(codigo_http http://$H/)" = 200 ] && estables=$((estables+1)); sleep 0.2; done
igual "tras reiniciar, la página responde 200 en 20 de 20 peticiones" 20 "$estables"
igual "la web (nginx) alcanza la API por el nombre estable" 200 "$(dentro $NS curl -s -o /dev/null -m 5 -w '%{http_code}' http://bitacora-web/api/info)"

titulo "Por qué el NodePort responde en todos los nodos: externalTrafficPolicy (Cluster contra Local)"
cp="$(kind get nodes --name "$CLUSTER" | grep control-plane)"; cp_ip="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' $cp)"
desde="$(kind get nodes --name "$CLUSTER" | grep worker | head -1)"   # la petición viene de otro nodo (el tráfico local del propio nodo no pasa por la política)
en_cp() { docker exec $desde curl -s -o /dev/null -m 5 -w '%{http_code}' -H 'Host: bitacora.localhost' http://$cp_ip:30080/api/info || true; }
nota "proxies en: $(k -n envoy-gateway-system get pods -l app.kubernetes.io/component=proxy -o jsonpath='{range .items[*]}{.spec.nodeName}{" "}{end}'); el plano de control no aloja ninguno"
igual "política del Service del proxy" Cluster "$(k -n envoy-gateway-system get svc $svc -o jsonpath='{.spec.externalTrafficPolicy}')"
igual "con Cluster, el NodePort responde también en el plano de control (sin proxy ahí)" 200 "$(en_cp)"
k -n envoy-gateway-system patch svc $svc -p '{"spec":{"externalTrafficPolicy":"Local"}}' >/dev/null; sleep 6
igual "con Local, en el plano de control (sin proxy) el NodePort no responde" 000 "$(en_cp)"
nodo_proxy="$(k -n envoy-gateway-system get pods -l app.kubernetes.io/component=proxy -o jsonpath='{range .items[*]}{.spec.nodeName}{" "}{.status.hostIP}{"\n"}{end}' | awk -v d="$desde" '$1 != d {print $2; exit}')"
igual "con Local, en otro nodo que aloja un proxy sí responde" 200 "$(docker exec $desde curl -s -o /dev/null -m 5 -w '%{http_code}' -H 'Host: bitacora.localhost' http://$nodo_proxy:30080/api/info || true)"
registrar "externalTrafficPolicy: Cluster -> 200 en un nodo sin proxy; Local -> 000 en un nodo sin proxy (y 200 en uno con proxy)"
k -n envoy-gateway-system patch svc $svc -p '{"spec":{"externalTrafficPolicy":"Cluster"}}' >/dev/null; sleep 6
igual "vuelve a Cluster y responde otra vez" 200 "$(en_cp)"

titulo "Paso 9: el Ingress equivalente"
k apply --dry-run=server -f "$SOL/ingress-equivalente.yaml" | sed 's/^/     /'
ok "el Ingress es válido para la API (sin controlador de Ingress en el clúster no se atiende)"

titulo "Criterio: ningún Service es LoadBalancer ni NodePort en la aplicación"
tipos="$(kn get svc -o jsonpath='{range .items[*]}{.spec.type}{"\n"}{end}' | sort -u | tr '\n' ' ')"
igual "tipos de Service en bitacora" "ClusterIP " "$tipos"
nota "el del controlador: $(k -n envoy-gateway-system get svc $svc -o jsonpath='{.spec.type}')"

titulo "Limpieza"
k delete ns $NS --wait >/dev/null && ok "espacio de nombres borrado"
terminar "Kubernetes 05"
