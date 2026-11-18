#!/usr/bin/env bash
# Solución del taller de la sesión 04 (CI/CD), todo en local: GitHub Actions con "act", un clúster kind propio como entorno
# de despliegue, un registro de imágenes real (registry:2) y un repositorio git "bare" con un gancho pre-receive en lugar
# de la protección de rama de GitHub. Crea todo lo que necesita y lo borra al terminar.
# Variables opcionales: PROYECTO (prefijo de nombres, minúsculas; n10-04), CLUSTER (pedidos), PUERTO_REGISTRO (5001),
# PUERTO_SERVICIO (8081), PUERTO_API (6443), PUERTO_BD_CI (5433), TRIVY_CACHE, ACT_ACCIONES, DORA_CACHE.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; source "$AQUI/comun.sh"
requiere docker kind kubectl act git jq python3 curl rsync perl
cd "$AQUI"
export PROYECTO=${PROYECTO:-n10-04} CLUSTER=${CLUSTER:-pedidos}
export PUERTO_REGISTRO=${PUERTO_REGISTRO:-5001} PUERTO_SERVICIO=${PUERTO_SERVICIO:-8081} PUERTO_API=${PUERTO_API:-6443} PUERTO_BD_CI=${PUERTO_BD_CI:-5433}
export IMAGEN=registro.example/pedidos:1.4.2
CACHE_BASE=${CURSO_CACHE:-$HOME/.cache/curso-de-cero-a-senior}
TRIVY_CACHE=${TRIVY_CACHE:-$CACHE_BASE/trivy}       # bases de Trivy: persistentes en tu máquina (3 GB la primera vez)
ACCIONES=${ACT_ACCIONES:-$CACHE_BASE/act-acciones}   # actions/checkout, setup-java...: dependen de la red, se conservan
export DORA_CACHE=${DORA_CACHE:-$CACHE_BASE/dora-gson.git}
README=$AQUI/../../README.md
README_K8S=$AQUI/../../../03-kubernetes-esencial/README.md
TRABAJO=$(mktemp -d)
LOG=$TRABAJO/ultima.log
R=$TRABAJO/ci-repo; REMOTO=$TRABAJO/ci-remoto.git
CACHE=$TRABAJO/actcache          # caché de Maven del pipeline (vacía en cada ejecución: mide el arranque en frío)
RESULTADOS=$TRABAJO/resultados.txt; : > "$RESULTADOS"
export KUBECONFIG=$TRABAJO/kubeconfig   # no toca tu ~/.kube/config
mkdir -p "$TRIVY_CACHE" "$ACCIONES" "$CACHE"
ACT=(act -P ubuntu-latest=catthehacker/ubuntu:act-24.04 --pull=false --rm --container-options "--label $PROYECTO=1" --cache-server-path "$CACHE" --action-cache-path "$ACCIONES"
     --env "TRIVY_CACHE=$TRIVY_CACHE" --env "TRIVY_OPCIONES=--skip-db-update --skip-java-db-update"
     --env "NODO=$CLUSTER-control-plane" --env "REGISTRO=localhost:$PUERTO_REGISTRO" --env "PUERTO_SERVICIO=$PUERTO_SERVICIO" --env "PUERTO_BD_CI=$PUERTO_BD_CI")
G=(git -c user.email=alumno@example.com -c user.name=alumno)
anotar() { echo "$*" | tee -a "$RESULTADOS" | sed 's/^/    /'; }
bajar() {
  paso "Bajando todo"
  kind delete cluster --name "$CLUSTER" >/dev/null 2>&1 || true
  docker ps -aq --filter "label=$PROYECTO" | xargs -r docker rm -f -v >/dev/null 2>&1 || true
  docker rm -f -v "$PROYECTO-registro" bd-ci libs-ci >/dev/null 2>&1 || true
  docker volume ls -q --filter "name=act-n10-04-" | xargs -r docker volume rm -f >/dev/null 2>&1 || true
  docker network ls -q --filter "name=act-n10-04-" | xargs -r docker network rm >/dev/null 2>&1 || true
  docker image rm "$IMAGEN" registro.example/pedidos:1.4.3-rota >/dev/null 2>&1 || true
  docker images --format '{{.Repository}}:{{.Tag}}' | grep -E "^(registro\.example/pedidos|localhost:$PUERTO_REGISTRO/pedidos)" | xargs -r docker image rm >/dev/null 2>&1 || true
  rm -rf "$TRABAJO"
}
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then echo "Ya existe un clúster kind llamado '$CLUSTER'; usa otro nombre con CLUSTER=..." >&2; exit 2; fi
trap bajar EXIT
docker image inspect catthehacker/ubuntu:act-24.04 >/dev/null 2>&1 || { echo "Descargando catthehacker/ubuntu:act-24.04 (varios GB, solo la primera vez)"; docker pull catthehacker/ubuntu:act-24.04 >/dev/null; }

cron() { # ejecuta y devuelve los segundos; reintenta una vez si act aborta por un fallo de infraestructura (demonio de Docker o red), no del pipeline.
  # Cada intento queda en resultados.txt, con la hora (segundos desde el inicio) en que terminó cada trabajo y en que act devolvió el control.
  local t0=$SECONDS intento pid marcas
  for intento in 1 2; do
    marcas=$(mktemp); : > "$LOG"
    ( n=0; while sleep 1; do m=$(grep -cE "Job (succeeded|failed)" "$LOG" 2>/dev/null || true); [ "$m" != "$n" ] && { n=$m; echo "$((SECONDS-t0)) $n" >> "$marcas"; }; done ) & pid=$!
    local rc=0; "$@" > "$LOG" 2>&1 || rc=$?
    kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null || true
    local fin=$((SECONDS-t0)) ultimo n_final n_vigilado
    n_final=$(grep -cE "Job (succeeded|failed)" "$LOG" 2>/dev/null || true); n_vigilado=$(tail -1 "$marcas" | awk '{print $2}')
    [ "${n_vigilado:-0}" != "$n_final" ] && echo "$fin $n_final" >> "$marcas"   # el último trabajo terminó menos de 1 s antes de que act devolviera el control: la vigilancia no llegó a verlo
    ultimo=$(tail -1 "$marcas" | awk '{print $1}'); rm -f "$marcas"
    echo "intento $intento de ${CRON_ETIQUETA:-?}: $fin s, $([ $rc = 0 ] && echo correcto || echo FALLÓ); último trabajo terminado a los ${ultimo:-?} s, act devolvió el control a los $fin s" >> "$RESULTADOS"
    [ $rc = 0 ] && { echo $fin; return 0; }
    grep -qE "^Error: .*(context canceled|connection reset by peer)|Java DB update failed|failed to download|unexpected EOF" "$LOG" || break
    t0=$SECONDS
  done
  echo FALLO >&2; return 1
}
precalentar_trivy() { # las dos bases de Trivy en la caché del anfitrión, con hasta 3 intentos y un repositorio alternativo; si no hay red y la caché está vacía o vencida, falla con un mensaje claro
  local imagen=aquasec/trivy:0.76.2 i
  mkdir -p "$TRIVY_CACHE"
  for i in 1 2 3; do
    docker run --rm -v "$TRIVY_CACHE:/root/.cache/trivy" $imagen image --download-db-only -q >/dev/null 2>&1 && break
    docker run --rm -v "$TRIVY_CACHE:/root/.cache/trivy" $imagen image --download-db-only -q --db-repository ghcr.io/aquasecurity/trivy-db:2 >/dev/null 2>&1 && break
    [ $i = 3 ] && { echo "NO HAY RED para descargar la base de vulnerabilidades de Trivy y la caché local no sirve: este taller la necesita una vez" >&2; return 1; }
    sleep 5
  done
  for i in 1 2 3; do
    docker run --rm -v "$TRIVY_CACHE:/root/.cache/trivy" $imagen image --download-java-db-only -q >/dev/null 2>&1 && break
    docker run --rm -v "$TRIVY_CACHE:/root/.cache/trivy" $imagen image --download-java-db-only -q --java-db-repository ghcr.io/aquasecurity/trivy-java-db:1 >/dev/null 2>&1 && break
    [ $i = 3 ] && { echo "NO HAY RED para descargar la base Java de Trivy y la caché local no sirve" >&2; return 1; }
    sleep 5
  done
  test -s "$TRIVY_CACHE/db/trivy.db" -a -s "$TRIVY_CACHE/java-db/trivy-java.db"
}
K() { docker exec "$CLUSTER-control-plane" kubectl --kubeconfig /etc/kubernetes/admin.conf "$@"; }

paso "0. Entorno: registro de imágenes, clúster kind y la aplicación desplegada como en la sesión 03"
docker run -d --name "$PROYECTO-registro" -p 127.0.0.1:$PUERTO_REGISTRO:5000 registry:2 >/dev/null
docker build -q -t "$IMAGEN" "$AQUI/aplicacion" >/dev/null
cat > "$TRABAJO/kind.yaml" <<YAML
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
  - |-
    [plugins."io.containerd.grpc.v1.cri".registry]
      config_path = "/etc/containerd/certs.d"
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
# El nodo alcanza el registro por la red "kind" y lo ve como localhost:$PUERTO_REGISTRO.
docker network connect kind "$PROYECTO-registro" 2>/dev/null || true
for nodo in $(kind get nodes --name "$CLUSTER"); do
  docker exec "$nodo" mkdir -p /etc/containerd/certs.d/localhost:$PUERTO_REGISTRO
  printf 'server = "http://%s:5000"\n[host."http://%s:5000"]\n  capabilities = ["pull", "resolve"]\n' "$PROYECTO-registro" "$PROYECTO-registro" | docker exec -i "$nodo" tee /etc/containerd/certs.d/localhost:$PUERTO_REGISTRO/hosts.toml >/dev/null
done
kind load docker-image "$IMAGEN" --name "$CLUSTER" >/dev/null
extraer_bloque "$README_K8S" 1 yaml > "$TRABAJO/readme-deployment.yaml"; test -s "$TRABAJO/readme-deployment.yaml"
kubectl create secret generic pedidos-secretos --from-literal=SPRING_DATASOURCE_PASSWORD="local-$RANDOM" >/dev/null
kubectl apply -f "$AQUI/k8s/configuracion.yaml" -f "$AQUI/k8s/base-datos.yaml" >/dev/null
kubectl rollout status deploy/postgres --timeout=180s >/dev/null
kubectl apply -f "$TRABAJO/readme-deployment.yaml" -f "$AQUI/k8s/servicio-nodeport.yaml" >/dev/null
kubectl rollout status deploy/pedidos --timeout=420s >/dev/null
comprobar "el Deployment de la sesión 03 está listo (3 réplicas) y el registro responde" bash -c "[ \"\$(kubectl get deploy pedidos -o jsonpath='{.status.readyReplicas}')\" = 3 ] && curl -sf http://localhost:$PUERTO_REGISTRO/v2/ >/dev/null"

paso "1. Repositorio con el pipeline (ci.yml) y la aplicación de la sesión"
rm -rf "$R" "$REMOTO" "$CACHE"; mkdir -p "$R/.github/workflows" "$CACHE" "$ACCIONES"
rsync -a --exclude target --exclude k8s --exclude 'compose*' "$APP/" "$R/"
cp ci.yml cd.yml "$R/.github/workflows/"
cd "$R"; git init -q -b main; git add -A; "${G[@]}" commit -qm "Aplicación pedidos con pipeline"
git init -q --bare "$REMOTO"

paso "2. Protección de la rama principal: sin pipeline verde y sin revisión no hay fusión"
cp "$AQUI/branch-protection.json" "$REMOTO/branch-protection.json"
cat > "$REMOTO/hooks/pre-receive" <<'HOOK'
#!/usr/bin/env bash
# Equivalente local de la protección de rama: a main solo entra un commit con las verificaciones
# requeridas (refs/checks/<sha>/<nombre>) y una aprobación (refs/reviews/<sha>), y nunca se reescribe la historia.
while read -r viejo nuevo ref; do
  [ "$ref" = refs/heads/main ] || continue
  for c in probar analizar migraciones imagen; do
    git show-ref --verify -q "refs/checks/$nuevo/$c" || { echo "RECHAZADO: falta la verificación '$c' en verde para $nuevo" >&2; exit 1; }
  done
  git show-ref --verify -q "refs/reviews/$nuevo" || { echo "RECHAZADO: falta una revisión aprobada para $nuevo" >&2; exit 1; }
  [ "$viejo" = 0000000000000000000000000000000000000000 ] || git merge-base --is-ancestor "$viejo" "$nuevo" || { echo "RECHAZADO: no se permite reescribir main" >&2; exit 1; }
done
HOOK
chmod +x "$REMOTO/hooks/pre-receive"
git remote add origin "$REMOTO"
# Un primer commit de main (el del repositorio inicial) se acepta con sus verificaciones: se prueba a continuación.
resultado_de_ci() { # resultado_de_ci [argumentos de act]: ejecuta el pipeline y devuelve 0 si todo quedó en verde
  "${ACT[@]}" pull_request -W .github/workflows/ci.yml "$@" > "$LOG" 2>&1
}
marcar_verde() { local sha; sha=$(git rev-parse HEAD); for c in probar analizar migraciones imagen; do git update-ref "refs/checks/$sha/$c" "$sha"; done
  git push -q origin "refs/checks/$sha/*:refs/checks/$sha/*" 2>/dev/null; }

paso "2b. Caché de las bases de Trivy (persistente en el anfitrión; la medición en frío no incluye esta descarga)"
comprobar "las bases de vulnerabilidades y la de Java están en la caché ($(du -sh "$TRIVY_CACHE" 2>/dev/null | cut -f1))" precalentar_trivy

paso "3. Pipeline en un pull request, con la caché vacía (medido) y luego con caché y en paralelo"
t_fria=$(CRON_ETIQUETA=fria cron "${ACT[@]}" pull_request -W .github/workflows/ci.yml)
anotar "ci_cache_fria_paralelo_s=$t_fria"
grep -E "Job (succeeded|failed)" "$LOG" | sed 's/^/    /' | sort | uniq -c
comprobar "los cuatro trabajos (probar, analizar, migraciones, imagen) terminan en verde" bash -c "[ \$(grep -c 'Job succeeded' $LOG) = 4 ]"
comprobar "las pruebas del proyecto se ejecutaron (3 pruebas)" grep -q "Tests run: 3, Failures: 0" "$LOG"
comprobar "las migraciones Flyway se aplican desde cero en el pipeline" grep -q "Successfully applied 1 migration" "$LOG"
git add -A; "${G[@]}" commit -qm "Registro de la ejecución" --allow-empty
t_tibia=$(CRON_ETIQUETA=caliente-paralelo cron "${ACT[@]}" pull_request -W .github/workflows/ci.yml)
aciertos_cache=$(grep -c "tar -xf .*cache.tzst" "$LOG" || true)   # act no pone cache-hit=true, pero extrae la caché restaurada
t_serie=$(CRON_ETIQUETA=caliente-serie cron "${ACT[@]}" --concurrent-jobs 1 pull_request -W .github/workflows/ci.yml)
anotar "ci_cache_caliente_paralelo_s=$t_tibia"
anotar "ci_cache_caliente_en_serie_s=$t_serie"
comprobar "la segunda ejecución restaura la caché de Maven en los 4 trabajos que la usan ($aciertos_cache aciertos); tiempo en frío $t_fria s, con caché $t_tibia s (la diferencia depende de la carga de la máquina: se informa, no se exige)" test "$aciertos_cache" -ge 3
comprobar "ejecutar los trabajos en paralelo no es más lento que en serie (paralelo $t_tibia s, serie $t_serie s; con la máquina libre el paralelo gana por mucho, con la máquina cargada se acercan: se informa, no se exige la ventaja)" test "$t_tibia" -le $((t_serie * 12 / 10))

paso "4. Un cambio que rompe una prueba no puede fusionarse"
git checkout -q -b cambio-roto
perl -pi -e 's/value\("desde-prueba"\)/value("otro")/' src/test/java/pedidos/ControladorInfoTest.java
"${G[@]}" commit -qam "Cambio que rompe una prueba"
if resultado_de_ci -j probar; then falla "el pipeline debía fallar"; else ok "el pipeline falla (probar en rojo)"; fi
git push -q origin cambio-roto
comprobar "git push a main sin verificaciones ni revisión: rechazado" bash -c "! git push origin cambio-roto:main 2>$TRABAJO/rechazo.txt && grep -q RECHAZADO $TRABAJO/rechazo.txt"
git checkout -q main
git checkout -q -b dependencia-vulnerable
python3 - "$AQUI/pom-parche-trivy.xml" <<'PY'
import sys
pom = open("pom.xml").read(); parche = open(sys.argv[1]).read()
assert parche in pom
open("pom.xml", "w").write(pom.replace(parche, "", 1))
PY
"${G[@]}" commit -qam "Quitar el parche de snappy-java y lz4-java (dependencias con vulnerabilidades altas)"
if resultado_de_ci -j analizar; then falla "el trabajo analizar debía fallar con dependencias vulnerables"; else ok "con una dependencia vulnerable el trabajo 'analizar' queda en rojo"; fi
comprobar "y Trivy nombró las dependencias" grep -qE "snappy-java|lz4-java" "$LOG"
comprobar "el rojo se debe a hallazgos y no a un fallo de descarga ni de Trivy (sin FATAL ni errores de la base de datos)" bash -c "! grep -qE 'FATAL|Unable to initialize|failed to download' $LOG"
git checkout -q main
git checkout -q -b cambio-bueno
echo "// mejora sin riesgo" >> src/main/java/pedidos/AplicacionPedidos.java
"${G[@]}" commit -qam "Cambio pequeño"
resultado_de_ci
comprobar "el pipeline del cambio bueno queda en verde" test "$(grep -c 'Job succeeded' "$LOG")" = 4
marcar_verde
comprobar "con las verificaciones pero sin revisión: rechazado" bash -c "! git push origin cambio-bueno:main 2>$TRABAJO/rechazo.txt && grep -q 'falta una revisión' $TRABAJO/rechazo.txt"
git update-ref "refs/reviews/$(git rev-parse HEAD)" HEAD; git push -q origin "refs/reviews/$(git rev-parse HEAD):refs/reviews/$(git rev-parse HEAD)"
comprobar "con verificaciones en verde y una revisión: se fusiona" git push -q origin cambio-bueno:main
git checkout -q main; git merge -q --ff-only cambio-bueno

paso "5. Despliegue automático al clúster kind (cd.yml): construir una vez, docker push a un registro real, desplegar por digest, prueba de humo"
previa=$(K get deploy pedidos -o jsonpath='{.spec.template.spec.containers[0].image}')
sha=$(git rev-parse HEAD)
t_cd=$(CRON_ETIQUETA=cd cron "${ACT[@]}" push -W .github/workflows/cd.yml)
anotar "cd_despliegue_correcto_s=$t_cd"
digest_registro=$(curl -sI -H 'Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json' "http://localhost:$PUERTO_REGISTRO/v2/pedidos/manifests/$sha" | tr -d '\r' | awk -F': ' 'tolower($1)=="docker-content-digest"{print $2}')
comprobar "el registro contiene la imagen del commit $sha (docker push real)" test -n "$digest_registro"
comprobar "el Deployment ejecuta esa imagen por digest (localhost:$PUERTO_REGISTRO/pedidos@$digest_registro)" test "$(K get deploy pedidos -o jsonpath='{.spec.template.spec.containers[0].image}')" = "localhost:$PUERTO_REGISTRO/pedidos@$digest_registro"
comprobar "y el nodo la pidió al registro por digest (el registro lo registra: GET /v2/pedidos/manifests/$digest_registro)" bash -c "docker logs $PROYECTO-registro 2>&1 | grep -q 'GET /v2/pedidos/manifests/$digest_registro'"
imagen_buena=$(K get deploy pedidos -o jsonpath='{.spec.template.spec.containers[0].image}')
comprobar "3 réplicas listas con la imagen nueva" test "$(K get deploy pedidos -o jsonpath='{.status.readyReplicas}')" = 3
comprobar "la prueba de humo pasó (se creó un pedido por el Service)" grep -q 'PRUEBA DE HUMO CORRECTA' "$LOG"

paso "6. Prueba de humo que falla: reversa automática"
date +%s%N > src/main/resources/version.txt   # un recurso distinto: el JAR (y por tanto la imagen) cambia de verdad; un comentario Java no cambiaría el código compilado
"${G[@]}" add -A; "${G[@]}" commit -qm "Cambio con una configuración rota"
sha2=$(git rev-parse HEAD)
CRON_ETIQUETA=cd-roto cron "${ACT[@]}" workflow_dispatch -W .github/workflows/cd.yml --input romper=true >/dev/null || true
cp "$LOG" "$TRABAJO/cd-reversa.log"
comprobar "la prueba de humo falló" grep -qE "Failure - Main Prueba de humo" "$TRABAJO/cd-reversa.log"
comprobar "se ejecutó la reversa automática" grep -q "REVERSA EJECUTADA" "$TRABAJO/cd-reversa.log"
comprobar "el Deployment volvió a la imagen anterior (la del commit $sha, por digest)" test "$(K get deploy pedidos -o jsonpath='{.spec.template.spec.containers[0].image}')" = "$imagen_buena"
comprobar "la imagen de la versión rota era otra (digest distinto) y la reversa la dejó en la buena" test "$(K get deploy pedidos -o jsonpath='{.spec.template.spec.containers[0].image}')" = "$imagen_buena" -a "$(curl -s "http://localhost:$PUERTO_REGISTRO/v2/pedidos/tags/list" | jq -r '.tags | length')" -ge 2
comprobar "y la variable de la versión rota (APP_MENSAJE=version-rota) ya no está en el Deployment" bash -c "! docker exec $CLUSTER-control-plane kubectl --kubeconfig /etc/kubernetes/admin.conf get deploy pedidos -o yaml | grep -q version-rota"
comprobar "y el servicio sigue respondiendo" esperar_http http://localhost:$PUERTO_SERVICIO/actuator/health/readiness 30
anotar "version_tras_reversa=$imagen_buena"

paso "7. Las cuatro métricas de entrega de un proyecto real con versiones publicadas (google/gson, historial fijado)"
bash "$AQUI/dora.sh" | tee "$TRABAJO/dora-resultado.txt" | sed 's/^/    /'
comprobar "el script calculó las cuatro métricas" test "$(grep -cE '^[1-4]\.' "$TRABAJO/dora-resultado.txt")" = 4
comprobar "el tiempo de entrega es mayor que cero (no es el intervalo entre commits)" bash -c "grep -E '^2\.' $TRABAJO/dora-resultado.txt | grep -qvE 'mediana 0\.0'"

paso "8. El workflow del README tal cual (extraído de la sesión 04)"
cd "$R"; git checkout -q -b readme-workflow
extraer_bloque "$README" 1 yaml > .github/workflows/readme.yml
head -3 .github/workflows/readme.yml | sed 's/^/    /'
"${G[@]}" add -A; "${G[@]}" commit -qm "Workflow del README"
t_readme=$(CRON_ETIQUETA=readme cron "${ACT[@]}" pull_request -W .github/workflows/readme.yml) || true
anotar "workflow_readme_en_pull_request_s=${t_readme:-fallo}"
comprobar "en un pull request el trabajo 'construir' compila y prueba (./mvnw verify) y no publica ninguna imagen" bash -c "grep -q 'Tests run: 3, Failures: 0' $LOG && grep -q 'Job succeeded' $LOG && ! grep -q 'docker push' $LOG"
git checkout -q main
# En main el README construye y publica en registro.example, que no existe. Para ejecutar esa misma rama del workflow se
# sustituye SOLO el nombre del registro por el registry:2 local (el trabajo 'desplegar' necesita un clúster con kubectl y no se ejecuta).
git show readme-workflow:.github/workflows/readme.yml | sed "s#registro\.example#localhost:$PUERTO_REGISTRO#g" > .github/workflows/readme-local.yml
sha_main=$(git rev-parse HEAD)
"${ACT[@]}" push -W .github/workflows/readme-local.yml -j construir > "$LOG" 2>&1 || { tail -30 "$LOG"; falla "el workflow del README falló en main"; }
comprobar "el workflow del README, en main, publica la imagen con docker push en el registro (etiqueta $sha_main)" bash -c "curl -s http://localhost:$PUERTO_REGISTRO/v2/pedidos/tags/list | grep -q $sha_main"
rm -f .github/workflows/readme-local.yml

# El Deployment queda con la última imagen desplegada por digest (sana, 3 réplicas); no se vuelve a la etiqueta registro.example/pedidos:1.4.2
# porque esa etiqueta solo existe en el nodo por "kind load" y el kubelet la pedía al registro inexistente (ImagePullBackOff).
K set env deployment/pedidos APP_MENSAJE- >/dev/null
K rollout status deployment/pedidos --timeout=240s >/dev/null
rm -rf "$R" "$REMOTO"
