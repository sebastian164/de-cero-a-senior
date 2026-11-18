#!/usr/bin/env bash
# Solución del taller de la sesión 03 (Kubernetes esencial): Deployment con tres sondas, ConfigMap y Secret, pod borrado,
# escalado, versión que no arranca, OOMKilled y carga durante un despliegue. Crea su propio clúster kind y lo borra al terminar.
# Variables opcionales: CLUSTER (nombre del clúster kind; por defecto pedidos), PUERTO_SERVICIO (8081), PUERTO_API (6443),
# PUERTO_FORWARD (8083), DURACION_CARGA (70, segundos de k6 por variante), MS_PETICION (8000).
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; source "$AQUI/comun.sh"
requiere docker kind kubectl k6 curl jq
cd "$AQUI"
export CLUSTER=${CLUSTER:-pedidos}
export PUERTO_SERVICIO=${PUERTO_SERVICIO:-8081} PUERTO_API=${PUERTO_API:-6443} PUERTO_FORWARD=${PUERTO_FORWARD:-8083}
export IMAGEN=registro.example/pedidos:1.4.2   # el nombre que usa el README de la sesión (no existe: la imagen se carga en kind)
README=$AQUI/../../README.md
TRABAJO=$(mktemp -d)
export KUBECONFIG=$TRABAJO/kubeconfig   # no toca tu ~/.kube/config
bajar() {
  kind delete cluster --name "$CLUSTER" >/dev/null 2>&1 || true
  docker image rm "$IMAGEN" registro.example/pedidos:1.4.3-rota >/dev/null 2>&1 || true
  rm -rf "$TRABAJO"
}
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then echo "Ya existe un clúster kind llamado '$CLUSTER'; usa otro nombre con CLUSTER=..." >&2; exit 2; fi
trap bajar EXIT

paso "0. Imagen, clúster kind '$CLUSTER' y manifiestos del README (Deployment tal cual)"
docker build -q -t "$IMAGEN" "$AQUI/aplicacion" >/dev/null
cat > "$TRABAJO/kind.yaml" <<YAML
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
networking:
  apiServerAddress: "127.0.0.1"
  apiServerPort: $PUERTO_API
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30081
        hostPort: $PUERTO_SERVICIO
        listenAddress: "127.0.0.1"
YAML
kind create cluster --name "$CLUSTER" --config "$TRABAJO/kind.yaml" --kubeconfig "$KUBECONFIG" >/dev/null
kind load docker-image "$IMAGEN" --name "$CLUSTER" >/dev/null
ok "imagen $IMAGEN cargada en el nodo (imagePullPolicy IfNotPresent: no se descarga)"
extraer_bloque "$README" 1 yaml > "$TRABAJO/readme-deployment.yaml"
test -s "$TRABAJO/readme-deployment.yaml"
kubectl create secret generic pedidos-secretos --from-literal=SPRING_DATASOURCE_PASSWORD="local-$RANDOM" >/dev/null
kubectl apply -f "$AQUI/k8s/configuracion.yaml" -f "$AQUI/k8s/base-datos.yaml" >/dev/null
kubectl rollout status deploy/postgres --timeout=180s >/dev/null
kubectl apply -f "$TRABAJO/readme-deployment.yaml" -f "$AQUI/k8s/servicio-nodeport.yaml" >/dev/null
kubectl rollout status deploy/pedidos --timeout=420s >/dev/null
comprobar "3 réplicas disponibles" test "$(kubectl get deploy pedidos -o jsonpath='{.status.availableReplicas}')" = 3
comprobar "las tres sondas están declaradas" test "$(kubectl get deploy pedidos -o jsonpath='{.spec.template.spec.containers[0].startupProbe.httpGet.path} {.spec.template.spec.containers[0].livenessProbe.httpGet.path} {.spec.template.spec.containers[0].readinessProbe.httpGet.path}')" = "/actuator/health/liveness /actuator/health/liveness /actuator/health/readiness"
comprobar "el Service responde a través del NodePort" esperar_http http://localhost:$PUERTO_SERVICIO/actuator/health/readiness 60
comprobar "la configuración llega desde el ConfigMap" bash -c "curl -s localhost:$PUERTO_SERVICIO/api/info | grep -q hola-desde-kubernetes"
comprobar "el pedido se guarda con la clave del Secret" bash -c "curl -sf -XPOST localhost:$PUERTO_SERVICIO/api/pedidos -H 'Content-Type: application/json' -d '{\"clienteId\":1,\"total\":10}' >/dev/null"
comprobar "la JVM toma el 75 % del límite de 768Mi (unos 576 MB de montón)" bash -c "m=\$(curl -s localhost:$PUERTO_SERVICIO/api/info | jq .memoriaMaximaMb); test \"\$m\" -ge 500 -a \"\$m\" -le 580"

listos() { kubectl get deploy "$1" -o jsonpath='{.status.readyReplicas}'; }
esperar_listos() { # esperar_listos deploy n segundos
  local i; for i in $(seq 1 "$3"); do [ "$(listos "$1")" = "$2" ] && return 0; sleep 1; done; return 1
}

paso "1-2. Deployment, Service, ConfigMap, Secret y las tres sondas"
comprobar "Deployment con 3 réplicas listas" esperar_listos pedidos 3 120
comprobar "ConfigMap aplicado como entorno" bash -c 'kubectl exec deploy/pedidos -- printenv APP_AMBIENTE | grep -qx kind'
comprobar "Secret aplicado como entorno (sin estar en ningún archivo)" bash -c 'kubectl exec deploy/pedidos -- printenv SPRING_DATASOURCE_PASSWORD | grep -q "^local-"'
comprobar "el Secret solo está codificado en base64, no cifrado" bash -c 'kubectl get secret pedidos-secretos -o jsonpath="{.data.SPRING_DATASOURCE_PASSWORD}" | base64 -d | grep -q "^local-"'

paso "3. Borrar un pod y escalar a cinco"
victima=$(kubectl get pods -l app=pedidos -o jsonpath='{.items[0].metadata.name}')
kubectl delete pod "$victima" --wait=false >/dev/null
comprobar "el pod $victima se recrea con otro nombre y vuelven a ser 3" bash -c "sleep 3; for i in \$(seq 1 150); do n=\$(kubectl get deploy pedidos -o jsonpath='{.status.readyReplicas}'); kubectl get pod $victima >/dev/null 2>&1 || { [ \"\$n\" = 3 ] && exit 0; }; sleep 1; done; exit 1"
kubectl scale deploy/pedidos --replicas=5 >/dev/null
comprobar "5 réplicas listas" esperar_listos pedidos 5 300
kubectl scale deploy/pedidos --replicas=3 >/dev/null
esperar_listos pedidos 3 120

paso "4. Una versión que no arranca no tumba el servicio"
docker build -q -t registro.example/pedidos:1.4.3-rota -f "$AQUI/Dockerfile.no-arranca" "$TRABAJO" >/dev/null
kind load docker-image registro.example/pedidos:1.4.3-rota --name "$CLUSTER" >/dev/null
kubectl set image deploy/pedidos pedidos=registro.example/pedidos:1.4.3-rota >/dev/null
fallidas=0
for i in $(seq 1 40); do curl -sf -o /dev/null localhost:$PUERTO_SERVICIO/api/info || fallidas=$((fallidas+1)); sleep 1; done
estado=$(kubectl get pods -l app=pedidos -o jsonpath='{range .items[*]}{.spec.containers[0].image}={.status.containerStatuses[0].ready}{"\n"}{end}')
echo "$estado" | sed 's/^/    /'
comprobar "los 3 pods viejos siguen listos (maxUnavailable 25 % de 3 = 0)" test "$(echo "$estado" | grep -c '1.4.2=true')" = 3
comprobar "el pod nuevo no llega a estar listo" bash -c "! echo '$estado' | grep -q '1.4.3-rota=true'"
comprobar "0 peticiones fallidas durante el despliegue roto (fallaron: $fallidas)" test "$fallidas" = 0
comprobar "rollout status reconoce que no avanza" bash -c '! kubectl rollout status deploy/pedidos --timeout=5s >/dev/null 2>&1'
kubectl rollout undo deploy/pedidos >/dev/null
kubectl rollout status deploy/pedidos --timeout=300s >/dev/null
comprobar "tras rollout undo vuelve la 1.4.2" test "$(kubectl get deploy pedidos -o jsonpath='{.spec.template.spec.containers[0].image}')" = "$IMAGEN"

paso "5. OOMKilled: -Xmx1g con un límite de 300Mi"
kubectl apply -f "$AQUI/oom.yaml" >/dev/null
kubectl rollout status deploy/pedidos-oom --timeout=300s >/dev/null
pod=$(kubectl get pods -l app=pedidos-oom -o jsonpath='{.items[0].metadata.name}')
kubectl port-forward "pod/$pod" $PUERTO_FORWARD:8080 >/dev/null 2>&1 & pf=$!
esperar_http http://localhost:$PUERTO_FORWARD/actuator/health 30 || true
curl -s -m 60 -XPOST "localhost:$PUERTO_FORWARD/api/memoria?mb=500" >/dev/null || true
kill $pf 2>/dev/null || true
sleep 5
kubectl describe pod "$pod" | sed -n '/Last State/,/Exit Code/p' | sed 's/^/    /'
comprobar "describe muestra Reason: OOMKilled y Exit Code 137" bash -c "kubectl describe pod $pod | grep -A3 'Last State' | grep -q OOMKilled && kubectl describe pod $pod | grep -A4 'Last State' | grep -q 'Exit Code:    137'"
paso "5b. Ajuste: límite del README (768Mi), sin -Xmx y con MaxRAMPercentage=60 para dejar margen fuera del montón"
kubectl set env deploy/pedidos-oom JAVA_TOOL_OPTIONS- >/dev/null
kubectl patch deploy pedidos-oom --type=json -p='[{"op":"replace","path":"/spec/template/spec/containers/0/resources","value":{"requests":{"cpu":"100m","memory":"768Mi"},"limits":{"memory":"768Mi"}}},{"op":"add","path":"/spec/template/spec/containers/0/command","value":["java","-XX:MaxRAMPercentage=60","-jar","app.jar"]}]' >/dev/null
kubectl rollout status deploy/pedidos-oom --timeout=300s >/dev/null
pod=$(kubectl get pods -l app=pedidos-oom --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
kubectl port-forward "pod/$pod" $PUERTO_FORWARD:8080 >/dev/null 2>&1 & pf=$!
esperar_http http://localhost:$PUERTO_FORWARD/actuator/health 30 || true
codigo=$(curl -s -m 60 -o /dev/null -w '%{http_code}' -XPOST "localhost:$PUERTO_FORWARD/api/memoria?mb=700" || true)
curl -s -XDELETE localhost:$PUERTO_FORWARD/api/memoria >/dev/null || true
vivo=$(curl -s -o /dev/null -w '%{http_code}' localhost:$PUERTO_FORWARD/actuator/health || true)
kill $pf 2>/dev/null || true
reinicios=$(kubectl get pod "$pod" -o jsonpath='{.status.containerStatuses[0].restartCount}')
comprobar "pedir 700 MB da un 500 por OutOfMemoryError de montón (código $codigo)" test "$codigo" = 500
comprobar "el contenedor no se reinició (reinicios: $reinicios) y sigue sano ($vivo)" test "$reinicios" = 0 -a "$vivo" = 200
{ kubectl logs "$pod" | grep -o 'java.lang.OutOfMemoryError: Java heap space' | head -1 | sed 's/^/    log: /'; } || true
kubectl delete -f "$AQUI/oom.yaml" >/dev/null

paso "6. Carga durante un despliegue: apagado inmediato frente a apagado ordenado"
sin_terminando() { # espera a que no queden pods viejos terminando: solo deben medirse los de este reinicio
  local i; for i in $(seq 1 120); do [ "$(kubectl get pods -l app=pedidos --no-headers | wc -l | tr -d ' ')" = 3 ] && return 0; sleep 1; done; return 1
}
medir() { # medir etiqueta: lanza carga, reinicia el Deployment y devuelve las peticiones fallidas
  sin_terminando
  k6 run -q --summary-export="$TRABAJO/k6-$1.json" -e DURACION=${DURACION_CARGA:-70}s -e MS=${MS_PETICION:-8000} -e BASE=http://localhost:$PUERTO_SERVICIO "$AQUI/carga.js" >"$TRABAJO/k6-$1.log" 2>&1 & local k=$!
  sleep 8
  kubectl rollout restart deploy/pedidos >/dev/null
  kubectl rollout status deploy/pedidos --timeout=300s >/dev/null
  wait $k || true
  grep -o 'fallo estado=[^"]*' "$TRABAJO/k6-$1.log" | sort | uniq -c | sed 's/^/    /' >&2 || true
  jq '.metrics.checks.fails' "$TRABAJO/k6-$1.json"
}
# Una variable por vez, con la misma gracia (45 s) en las tres variantes y la misma carga. Desde Spring Boot 3.4 el
# apagado ordenado (graceful) es el valor por defecto, así que "sin apagado ordenado" exige pedir "immediate" a propósito.
#   (a) SERVER_SHUTDOWN=immediate, sin preStop     (b) graceful, sin preStop     (c) graceful, con preStop de 10 s
# Clasificación de cada fallo por la duración de la petición (que dura MS_PETICION ms, 8 s por defecto, si no la cortan): "cortada en vuelo" (más de 200 ms: la conexión ya
# estaba aceptada y se cerró con la petición dentro) o "rechazada al conectar" (menos de 200 ms: el Service aún enviaba tráfico a un pod que ya no escuchaba).
clasificar() { { grep -ho 'duracion_ms=[0-9]*' "$TRABAJO/k6-$1.log" | cut -d= -f2 | awk -v t=200 '{ if ($1>t) v++; else c++ } END { printf "cortadas_en_vuelo=%d rechazadas_al_conectar=%d", v, c }'; } || true; }
tipos() { { grep -ho 'fallo estado=[^"]*' "$TRABAJO/k6-$1.log" | sed 's/estado=\([0-9]*\) error=\([0-9]*\).*/estado=\1 error=\2/' | sort | uniq -c | tr '\n' ';'; } || true; }
configurar() { # configurar shutdown con_prestop(si|no)
  kubectl patch deploy pedidos --type=json -p='[{"op":"remove","path":"/spec/template/spec/containers/0/lifecycle"}]' >/dev/null 2>&1 || true
  kubectl set env deploy/pedidos SERVER_SHUTDOWN="$1" SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=20s >/dev/null
  kubectl patch deploy pedidos -p '{"spec":{"template":{"spec":{"terminationGracePeriodSeconds":45}}}}' >/dev/null
  if [ "$2" = si ]; then
    kubectl patch deploy pedidos --type=json -p='[{"op":"add","path":"/spec/template/spec/containers/0/lifecycle","value":{"preStop":{"sleep":{"seconds":10}}}}]' >/dev/null
  fi
  kubectl rollout status deploy/pedidos --timeout=300s >/dev/null
}
configurar immediate no; a=$(medir a-immediate-sin-prestop)
tipos_a=$(tipos a-immediate-sin-prestop); clase_a=$(clasificar a-immediate-sin-prestop)
configurar graceful no; b=$(medir b-graceful-sin-prestop)
tipos_b=$(tipos b-graceful-sin-prestop); clase_b=$(clasificar b-graceful-sin-prestop)
configurar graceful si; c=$(medir c-graceful-con-prestop)
tipos_c=$(tipos c-graceful-con-prestop); clase_c=$(clasificar c-graceful-con-prestop)
echo "    (a) immediate, sin preStop:  $a fallidas  [$tipos_a] $clase_a"
echo "    (b) graceful, sin preStop:   $b fallidas  [$tipos_b] $clase_b"
echo "    (c) graceful, con preStop:   $c fallidas  [$tipos_c] $clase_c"
comprobar "(a) con apagado inmediato fallan peticiones" test "$a" -gt 0
comprobar "(c) con apagado ordenado y preStop no falla ninguna" test "$c" = 0
vuelo() { echo "$1" | sed 's/.*cortadas_en_vuelo=\([0-9]*\).*/\1/'; }
comprobar "(a) el apagado inmediato corta al menos una petición en vuelo" test "$(vuelo "$clase_a")" -ge 1
comprobar "(b) el apagado ordenado sin preStop no corta ninguna petición en vuelo" test "$(vuelo "$clase_b")" = 0
printf 'a_immediate_sin_prestop=%s  tipos: %s  %s\nb_graceful_sin_prestop=%s  tipos: %s  %s\nc_graceful_con_prestop=%s  tipos: %s  %s\n' "$a" "$tipos_a" "$clase_a" "$b" "$tipos_b" "$clase_b" "$c" "$tipos_c" "$clase_c" > "$TRABAJO/resultados-apagado.txt"

# Deja el Deployment como lo describe el README.
kubectl apply -f "$TRABAJO/readme-deployment.yaml" >/dev/null
kubectl rollout status deploy/pedidos --timeout=300s >/dev/null

echo; sed "s/^/    /" "$TRABAJO/resultados-apagado.txt"
echo "Taller 03: todo correcto"
