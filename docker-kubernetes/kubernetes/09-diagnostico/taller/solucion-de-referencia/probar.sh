#!/usr/bin/env bash
# Solución ejecutable del taller Kubernetes 09: diagnostica con kubectl, corrige los archivos y comprueba el Service.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
preparar_cluster
SOL="$AQUI/averiado"
INICIO="$CURSO/kubernetes/09-diagnostico/taller/inicio/averiado"
NS=averiado
OUT="$SALIDAS/k8s-09.txt"; : > "$OUT"
kn() { k -n $NS "$@"; }
registrar() { echo "$1" >> "$OUT"; nota "$1"; }
k delete ns $NS --ignore-not-found --wait >/dev/null 2>&1

titulo "Aplicar los ocho casos averiados"
k apply -f "$INICIO/" >/dev/null
sleep 75
kn get pods --no-headers | awk '{print $1, $3}' | sed 's/-[a-z0-9]*-[a-z0-9]* / /' | sort -u | sed 's/^/     /'
estado() { kn get pods -l app=caso-$1 --no-headers | awk '{print $3}' | sort -u | tr '\n' ' ' | sed 's/ $//'; }
herramientas $NS
http() { dentro $NS curl -s -o /dev/null -m 4 -w '%{http_code}' "http://caso-$1/api/info" 2>/dev/null || true; }
evento() { kn get events --field-selector type=Warning -o jsonpath='{range .items[*]}{.involvedObject.name}{" | "}{.message}{"\n"}{end}' | grep "^caso-$1-" | head -1 | cut -d'|' -f2 | cut -c1-200; }

# caso | síntoma esperado | evidencia (orden que la delata)
titulo "Síntoma y evidencia, sin abrir los manifiestos"
igual "caso 1: imagen que no se descarga" ImagePullBackOff "$(estado 1 | awk '{print $1}')"
registrar "caso1: $(evento 1)"
afirmar "caso 1: el evento nombra la etiqueta 1.0.O con la letra O" sh -c "echo '$(evento 1)' | grep -q '1.0.O'"
esperar_hasta 120 sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS get pods -l app=caso-2 --no-headers | grep -q CrashLoop"
for _ in $(seq 120); do c2="$(estado 2)"; echo "$c2" | grep -q CrashLoop && break; sleep 1; done; registrar "caso2: estado=$c2 logs=$(kn logs $(kn get pod -l app=caso-2 -o name | head -1) --previous 2>/dev/null | grep -m1 NumberFormat | cut -c1-120)"
afirmar "caso 2: CrashLoopBackOff con NumberFormatException por un puerto con espacio" sh -c "echo '$c2' | grep -q CrashLoop && kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS logs -l app=caso-2 --previous --tail=-1 | grep -q 'NumberFormatException: For input string: \"8080 \"'"
registrar "caso3: estado=$(estado 3) log=$(kn logs $(kn get pod -l app=caso-3 -o name | head -1) | grep -m1 ERROR | cut -c32-170)"
afirmar "caso 3: Running pero 0/1; el registro dice Read-only file system" sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS logs -l app=caso-3 --tail=-1 | grep -q 'Read-only file system'"
registrar "caso4: pods $(estado 4) listos; endpoints=$(kn get endpointslices -l kubernetes.io/service-name=caso-4 -o jsonpath='{.items[0].endpoints}') selector=$(kn get svc caso-4 -o jsonpath='{.spec.selector}')"
igual "caso 4: pods Running y Service sin destinos" "" "$(kn get endpointslices -l kubernetes.io/service-name=caso-4 -o jsonpath='{.items[0].endpoints}' | sed 's/^null$//')"
igual "caso 5: Pending" Pending "$(estado 5)"
registrar "caso5: $(evento 5)"
ev5="$(evento 5)"
afirmar "caso 5: el evento habla de node affinity/selector" grep -q "node affinity/selector" <<< "$ev5"
registrar "caso6: estado=$(estado 6) liveness=$(kn get pod -l app=caso-6 -o jsonpath='{.items[0].spec.containers[0].livenessProbe.httpGet.path}') ultimo_codigo=$(kn get pod -l app=caso-6 -o jsonpath='{.items[0].status.containerStatuses[0].lastState.terminated.exitCode}')"
afirmar "caso 6: reinicios; el registro muestra GET /salud/viva 404 y apagado" sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS logs -l app=caso-6 --previous --tail=-1 | grep -q 'GET /salud/viva 404'"
igual "caso 7: CreateContainerConfigError" CreateContainerConfigError "$(estado 7 | awk '{print $1}')"
registrar "caso7: $(evento 7)"
registrar "caso8: estado=$(estado 8) lastState=$(kn get pod -l app=caso-8 -o jsonpath='{.items[0].status.containerStatuses[0].lastState.terminated.reason}/{.items[0].status.containerStatuses[0].lastState.terminated.exitCode}') memoria=$(kn get pod -l app=caso-8 -o jsonpath='{.items[0].spec.containers[0].resources.limits.memory}')"
igual "caso 8: OOMKilled 137" "OOMKilled/137" "$(kn get pod -l app=caso-8 -o jsonpath='{.items[0].status.containerStatuses[0].lastState.terminated.reason}/{.items[0].status.containerStatuses[0].lastState.terminated.exitCode}')"
sin200=0; for c in 1 2 3 4 5 6 7 8; do [ "$(http $c)" = 200 ] || sin200=$((sin200+1)); done
registrar "antes: casos que responden 200 = $((8 - sin200)) de 8"
igual "antes de arreglar, ninguno responde 200" 8 "$sin200"

titulo "Validación previa, sin clúster: kubeconform y kube-linter sobre los casos averiados"
kc="$(kubeconform -strict -summary "$INICIO" 2>&1 | tail -1)"; registrar "kubeconform -strict: $kc"
afirmar "kubeconform: 18 recursos, 18 válidos (ningún fallo es un error de esquema)" sh -c "echo '$kc' | grep -q 'Valid: 18, Invalid: 0, Errors: 0'"
kube-linter lint "$INICIO" --format json > "$SALIDAS/k8s-09-kube-linter.json" 2>/dev/null || true
python3 - "$SALIDAS/k8s-09-kube-linter.json" > "$SALIDAS/k8s-09-kube-linter.txt" <<'PY'
import json,sys,collections
d=json.load(open(sys.argv[1])); c=collections.defaultdict(set)
for r in d.get("Reports") or []: c[r["Check"]].add(r["Object"]["K8sObject"]["Name"])
for k in sorted(c): print(k, ",".join(sorted(c[k])))
PY
registrar "kube-linter (comprobaciones que señalan algo): $(cut -d' ' -f1 "$SALIDAS/k8s-09-kube-linter.txt" | tr '\n' ' ')"
igual "kube-linter: dangling-service solo señala al caso 4" "dangling-service caso-4" "$(grep '^dangling-service' "$SALIDAS/k8s-09-kube-linter.txt")"

titulo "Caso 4, fallo por fallo: primero solo el selector"
mkdir -p "$SALIDAS/k8s-09-caso4"
sed 's/^    app: caso4$/    app: caso-4/' "$INICIO/caso-4.yaml" > "$SALIDAS/k8s-09-caso4/caso-4.yaml"
afirmar "el único cambio es el selector del Service" sh -c "diff '$INICIO/caso-4.yaml' '$SALIDAS/k8s-09-caso4/caso-4.yaml' | grep -c '^[<>]' | grep -qx 2"
k apply -f "$SALIDAS/k8s-09-caso4/caso-4.yaml" >/dev/null
esperar_hasta 60 sh -c "[ -n \"\$(kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS get endpointslices -l kubernetes.io/service-name=caso-4 -o jsonpath='{.items[0].endpoints[0].addresses[0]}')\" ]"
destinos="$(kn get endpointslices -l kubernetes.io/service-name=caso-4 -o jsonpath='{range .items[0].endpoints[*]}{.addresses[0]}{" "}{end}')"
puerto="$(kn get endpointslices -l kubernetes.io/service-name=caso-4 -o jsonpath='{.items[0].ports[0].port}')"
registrar "caso4_solo_selector: destinos='$destinos' puerto_del_destino=$puerto http=$(http 4)"
afirmar "con solo el selector arreglado el Service ya tiene destinos" test -n "$destinos"
igual "pero el puerto de destino es el 80 (nadie escucha ahí)" 80 "$puerto"
igual "y /api/info sigue sin responder 200 (000)" 000 "$(http 4)"

titulo "Aplicar los archivos corregidos (kubectl apply -f, sin kubectl edit)"
diff -r "$INICIO" "$SOL" | grep -E '^[<>]' | sed 's/^/     /'
k apply -f "$SOL/" >/dev/null
sleep 80
for c in 1 2 3 4 5 6 7 8; do
  esperar_hasta 60 sh -c "[ \"\$(kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS get deploy caso-$c -o jsonpath='{.status.readyReplicas}')\" = 2 ]"
  igual "caso $c: dos copias listas y /api/info por el Service" "2 200" "$(kn get deploy caso-$c -o jsonpath='{.status.readyReplicas}') $(http $c)"
done
registrar "despues: ocho casos responden 200 por su Service"
registrar "caso4_segundo_fallo: comprobado arriba: con solo el selector arreglado el Service tenia destinos en el puerto 80 y /api/info daba 000; con targetPort: http responde 200"

titulo "Limpieza"
k delete ns $NS --wait >/dev/null && ok "espacio de nombres borrado"
terminar "Kubernetes 09"
