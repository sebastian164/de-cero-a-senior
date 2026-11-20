#!/usr/bin/env bash
# Solución ejecutable del taller Kubernetes 02. Puertos de port-forward: 50120 y 50121.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
preparar_cluster
SOL="$AQUI"
NS=bitacora
TMP="$(mktemp -d "${TMPDIR:-/tmp}/$PREFIJO-k02.XXXXXX")"
OUT="$SALIDAS/k8s-02.txt"; : > "$OUT"
kn() { k -n $NS "$@"; }
# Pods listos y sin marca de borrado con una etiqueta.
listos() { kn get pods -l "$1" -o json | python3 -c 'import json,sys; print(sum(1 for p in json.load(sys.stdin)["items"] if not p["metadata"].get("deletionTimestamp") and any(c["type"]=="Ready" and c["status"]=="True" for c in p["status"].get("conditions",[]))))'; }
hosts_distintos() { python3 -c 'import json,sys; print(len({json.loads(l)["host"] for l in sys.stdin if l.strip().startswith("{")}))'; }
hay_tres() { [ "$(listos app=bitacora-api)" = 3 ] && [ "$(kn get pods -l app=bitacora-api --no-headers | wc -l | tr -d ' ')" = 3 ]; }
pf() { kn port-forward "$1" "$2" >/dev/null 2>&1 & echo $!; }

k delete ns $NS otro-equipo --ignore-not-found --wait >/dev/null 2>&1

titulo "Kubernetes 02, paso 1: API con tres copias y Service ClusterIP"
k apply -f "$SOL/espacio-de-nombres.yaml" -f "$SOL/api.yaml"
kn rollout status deploy/bitacora-api --timeout=120s >/dev/null
igual "tres copias listas" 3 "$(listos app=bitacora-api)"
igual "tipo de Service" ClusterIP "$(kn get svc bitacora-api -o jsonpath='{.spec.type}')"

titulo "Paso 2: balanceo desde dentro del clúster"
herramientas $NS
veinte="$(dentro $NS sh -c 'for i in $(seq 20); do curl -s http://bitacora-api/api/info; echo; done')"
d_dentro="$(echo "$veinte" | hosts_distintos)"
nota "20 llamadas por el Service desde un pod: $d_dentro valores distintos de host"
echo "$veinte" | python3 -c 'import json,sys,collections; print("     ", dict(collections.Counter(json.loads(l)["host"] for l in sys.stdin if l.strip())))'
afirmar "el Service reparte entre varias copias" test "$d_dentro" -ge 2

titulo "Paso 3: lo mismo por port-forward al Service"
P=$(pf service/bitacora-api 50120:80); esperar_hasta 15 curl -sf localhost:50120/api/info
d_pf="$(for i in $(seq 20); do curl -s localhost:50120/api/info; echo; done | hosts_distintos)"
kill $P; wait $P 2>/dev/null
nota "20 llamadas por port-forward: $d_pf valor distinto de host"
igual "port-forward elige un pod y se queda con él" 1 "$d_pf"
echo "balanceo: dentro=$d_dentro portforward=$d_pf" >> "$OUT"

titulo "Paso 4: autorreparación"
uno="$(kn get pods -l app=bitacora-api -o name | head -1)"
i=$(ahora_ms); kn delete "$uno" --wait=false >/dev/null
esperar_hasta 120 hay_tres
t1=$(( $(ahora_ms) - i ))
dos="$(kn get pods -l app=bitacora-api -o name | head -2 | tr '\n' ' ')"
i=$(ahora_ms); kn delete $dos --wait=false >/dev/null
esperar_hasta 120 hay_tres
t2=$(( $(ahora_ms) - i ))
nota "vuelve a haber tres listas: tras borrar uno ${t1} ms; tras borrar dos ${t2} ms (incluye esperar a que terminen los borrados)"
duenio="$(kn get pods -l app=bitacora-api -o jsonpath='{.items[0].metadata.ownerReferences[0].kind}')"
igual "el dueño de los pods es el ReplicaSet" ReplicaSet "$duenio"
igual "y el del ReplicaSet, el Deployment" Deployment "$(kn get rs -l app=bitacora-api -o jsonpath='{.items[0].metadata.ownerReferences[0].kind}')"
echo "reposicion: uno=${t1}ms dos=${t2}ms" >> "$OUT"

titulo "Paso 5: la web con dos copias"
k apply -f "$SOL/web.yaml" >/dev/null
kn rollout status deploy/bitacora-web --timeout=120s >/dev/null
igual "dos copias de la web" 2 "$(listos app=bitacora-web)"
P=$(pf service/bitacora-web 50121:80); esperar_hasta 15 curl -sf localhost:50121/
afirmar "la página carga por port-forward" sh -c "curl -s localhost:50121/ | grep -q '<title>Bitácora</title>'"
igual "la web llega a la API por el nombre del Service" 200 "$(codigo_http localhost:50121/api/info)"
igual "y guarda notas" 201 "$(codigo_http -X POST --data 'nota desde la web' localhost:50121/api/notas)"

titulo "Paso 6: selector roto"
sed 's/^    app: bitacora-api$/    app: bitacora-apii/' "$SOL/api.yaml" > "$TMP/api-selector-roto.yaml"
grep -q 'app: bitacora-apii' "$TMP/api-selector-roto.yaml" || mal "no se generó el selector roto"
salida_apply="$(k apply -f "$TMP/api-selector-roto.yaml" 2>&1)"
nota "kubectl apply: $(echo "$salida_apply" | tr '\n' ' ')"
afirmar "apply no da error" sh -c "echo '$salida_apply' | grep -q 'service/bitacora-api configured'"
sleep 3
igual "el usuario ve un 502 en /api/info" 502 "$(codigo_http localhost:50121/api/info)"
ep="$(kn get endpointslices -l kubernetes.io/service-name=bitacora-api -o jsonpath='{.items[*].endpoints}' | sed 's/^null$//')"
kn get endpointslices -l kubernetes.io/service-name=bitacora-api | sed 's/^/     /'
igual "endpointslices: el Service no tiene destinos" "" "$ep"
k apply -f "$SOL/api.yaml" >/dev/null; sleep 3
igual "con el selector correcto vuelve a responder" 200 "$(codigo_http localhost:50121/api/info)"
echo "selector_roto: web=502 endpoints=vacio" >> "$OUT"

titulo "Paso 7: tres notas y listas distintas"
for n in uno dos tres; do curl -s -o /dev/null -X POST --data "nota $n" localhost:50121/api/notas; done
listas="$(for i in $(seq 15); do curl -s localhost:50121/api/notas | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["host"], len(d["notas"]))'; done | sort | uniq -c)"
echo "$listas" | sed 's/^/     /'
distintas="$(echo "$listas" | awk '{print $3}' | sort -u | wc -l | tr -d ' ')"
afirmar "la lista cambia según la copia que responde ($distintas tamaños distintos)" test "$distintas" -ge 2
echo "notas: $(echo "$listas" | tr '\n' ';')" >> "$OUT"
kill $P; wait $P 2>/dev/null

titulo "Paso 8: versión 1.0.1"
sed 's|bitacora-api:1.0.0|bitacora-api:1.0.1|' "$SOL/api.yaml" > "$TMP/api-1.0.1.yaml"
kn get rs -l app=bitacora-api -w --output-watch-events > "$TMP/rs.log" 2>&1 & W=$!
k apply -f "$TMP/api-1.0.1.yaml" >/dev/null
kn rollout status deploy/bitacora-api --timeout=120s | sed 's/^/     /'
sleep 2; kill $W; wait $W 2>/dev/null
nota "kubectl get replicaset -w (eventos):"; sed 's/^/       /' "$TMP/rs.log" | tail -12
kn get rs -l app=bitacora-api | sed 's/^/     /'
igual "dos ReplicaSet: el nuevo con 3 y el viejo con 0" "0 3" "$(kn get rs -l app=bitacora-api -o jsonpath='{range .items[*]}{.spec.replicas}{"\n"}{end}' | sort | tr '\n' ' ' | sed 's/ $//')"

titulo "Paso 9: etiqueta que no existe, con medición"
iniciar_medidor $NS http://bitacora-api/api/info
sed 's|bitacora-api:1.0.0|bitacora-api:9.9.9|' "$SOL/api.yaml" > "$TMP/api-mala.yaml"
k apply -f "$TMP/api-mala.yaml" >/dev/null
negar "rollout status no termina (se corta a los 45 s)" kn rollout status deploy/bitacora-api --timeout=45s
kn get pods -l app=bitacora-api --no-headers | sed 's/^/     /'
viejos="$(kn get pods -l app=bitacora-api --no-headers | grep -c Running)"
malos="$(kn get pods -l app=bitacora-api --no-headers | grep -cE 'ImagePull|ErrImage')"
nota "pods viejos atendiendo: $viejos; nuevos sin imagen: $malos"
igual "siguen las tres copias viejas" 3 "$viejos"
kn rollout undo deploy/bitacora-api | sed 's/^/     /'
kn rollout status deploy/bitacora-api --timeout=120s >/dev/null
sleep 3
read -r total errores <<< "$(resultados_medidor $NS)"
detener_medidor $NS
nota "bucle de medición durante el paso 9: $total peticiones, $errores errores"
igual "el servicio siguió disponible" 0 "$errores"
kn rollout history deploy/bitacora-api | sed 's/^/     /'
echo "etiqueta_mala: viejos=$viejos malos=$malos peticiones=$total errores=$errores" >> "$OUT"

titulo "Paso 10: kubectl scale contra apply"
kn scale deploy/bitacora-api --replicas=5 >/dev/null
kn rollout status deploy/bitacora-api --timeout=60s >/dev/null
igual "tras scale" 5 "$(kn get deploy bitacora-api -o jsonpath='{.spec.replicas}')"
k apply -f "$SOL/api.yaml" >/dev/null
igual "tras apply del archivo manda el archivo" 3 "$(kn get deploy bitacora-api -o jsonpath='{.spec.replicas}')"

titulo "Paso 11: desde otro espacio de nombres"
k apply -f "$SOL/otro-espacio.yaml" >/dev/null
herramientas otro-equipo
for nombre in bitacora-api bitacora-api.bitacora bitacora-api.bitacora.svc.cluster.local; do
  c=000
  for intento in 1 2 3; do   # bajo carga la primera consulta de DNS puede tardar más de 5 s
    c="$(dentro otro-equipo curl -s -o /dev/null -m 10 -w '%{http_code}' http://$nombre/api/info || true)"
    [ "$c" = 200 ] && break
  done
  nota "desde otro-equipo, http://$nombre -> $c"
  [ $nombre = bitacora-api ] && igual "el nombre corto no se resuelve desde otro espacio" 000 "$c"
  [ $nombre != bitacora-api ] && igual "$nombre responde" 200 "$c"
done

titulo "Limpieza"
k delete ns $NS otro-equipo --wait >/dev/null && ok "espacios de nombres borrados"
rm -rf "$TMP"
terminar "Kubernetes 02"
