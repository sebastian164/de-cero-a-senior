#!/usr/bin/env bash
# Solución ejecutable del taller Kubernetes 06: el estado.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
preparar_cluster
SOL="$AQUI"
NS=bitacora-06
OUT="$SALIDAS/k8s-06.txt"; : > "$OUT"
kn() { k -n $NS "$@"; }
# Espera a que el servidor definitivo acepte consultas por red (no solo el temporal de inicialización).
listo_db() { esperar_hasta 90 kn exec "$1" -- psql -h 127.0.0.1 -U postgres -c "select 1"; sleep 1; esperar_hasta 30 kn exec "$1" -- psql -h 127.0.0.1 -U postgres -c "select 1"; }
registrar() { echo "$1" >> "$OUT"; nota "$1"; }
psql_db() { kn exec db-0 -- psql -U postgres -d "${2:-bitacora}" -At -c "$1"; }
listas_distintas() { # 20 lecturas por el Service desde dentro; cuenta listas distintas
  dentro $NS sh -c 'for i in $(seq 20); do curl -s http://bitacora-api/api/notas | md5sum 2>/dev/null || curl -s http://bitacora-api/api/notas | cksum; done' | sort -u | wc -l | tr -d ' '; }
listas() { dentro $NS sh -c 'for i in $(seq 20); do curl -s --retry 8 --retry-all-errors --retry-delay 1 http://bitacora-api/api/notas; echo; done' | python3 -c '
import sys,json
s=set()
for l in sys.stdin:
    l=l.strip()
    if l: s.add(tuple(json.loads(l)["notas"]))
print(len(s))'; }

k delete ns $NS --ignore-not-found --wait >/dev/null 2>&1
k create ns $NS >/dev/null
herramientas $NS

titulo "Paso 1: tres copias sin volumen"
k apply -n $NS -f "$SOL/api-sin-volumen.yaml" >/dev/null
kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
for i in 1 2 3 4 5; do dentro $NS curl -s --retry 8 --retry-all-errors --retry-delay 1 -m 5 -o /dev/null -X POST --data "nota $i" http://bitacora-api/api/notas; done
# El Service reparte al azar: con 5 notas y 3 copias hay un 1 % de probabilidad de que todas caigan en la misma. Se sigue guardando hasta que
# al menos dos copias tengan notas (como máximo 15 más), para que la medición no dependa de la suerte.
con_notas() { for p in $(kn get pods -l app=bitacora-api -o name); do kn exec $p -- sh -c 'test -s /datos/notas.txt' 2>/dev/null && echo $p; done | wc -l | tr -d ' '; }
for extra in $(seq 15); do [ "$(con_notas)" -ge 2 ] && break; dentro $NS curl -s --retry 8 --retry-all-errors --retry-delay 1 -m 5 -o /dev/null -X POST --data "nota extra $extra" http://bitacora-api/api/notas; done
n="$(listas)"
registrar "paso1: 20 lecturas tras 5 notas -> $n listas distintas"
for p in $(kn get pods -l app=bitacora-api -o name); do echo "     $p guardó $(kn exec $p -- sh -c 'wc -l < /datos/notas.txt 2>/dev/null || true' 2>/dev/null) notas"; done
afirmar "hay varias listas distintas (cada copia escribe en su propio disco)" test "$n" -ge 2

titulo "Paso 2: reiniciar"
kn rollout restart deploy/bitacora-api >/dev/null; kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
tras="$(dentro $NS curl -s --retry 8 --retry-all-errors --retry-delay 1 -m 5 http://bitacora-api/api/notas | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["notas"]))')"
registrar "paso2: notas tras reiniciar = $tras"
igual "las notas se perdieron" 0 "$tras"

titulo "Paso 3: el mismo volumen ReadWriteOnce en tres copias"
k apply -n $NS -f "$SOL/api-volumen-compartido.yaml" >/dev/null
sleep 40
kn get pods -l app=bitacora-api -o wide --no-headers | awk '{print "     "$1, $2, $3, $7}'
kn get pvc | sed 's/^/     /'
nodos="$(kn get pods -l app=bitacora-api -o jsonpath='{range .items[*]}{.spec.nodeName}{"\n"}{end}' | sort -u | wc -l | tr -d ' ')"
estados="$(kn get pods -l app=bitacora-api --no-headers | awk '{print $3}' | sort | uniq -c | tr '\n' ';' | tr -s ' ')"
registrar "paso3: nodos_distintos=$nodos estados=$estados"
kn get events --field-selector type=Warning | tail -3 | sed 's/^/     /'
kn get pv -o jsonpath='{range .items[*]}{.metadata.name}{" afinidad="}{.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values}{"\n"}{end}' | sed 's/^/     /'
igual "todas las copias en un solo nodo (la afinidad del PV lo obliga)" 1 "$nodos"

titulo "Paso 4: StorageClass"
k get storageclass | sed 's/^/     /'
registrar "paso4: $(k get storageclass -o jsonpath='{range .items[*]}{.metadata.name}{" provisioner="}{.provisioner}{" binding="}{.volumeBindingMode}{" reclaim="}{.reclaimPolicy}{"\n"}{end}')"
registrar "paso4: modos ofrecidos de verdad: ReadWriteOnce (hostPath local-path); ReadWriteMany no se puede atender"

titulo "Paso 5 y 6: una sola copia con volumen y estrategia Recreate"
k apply -n $NS -f "$SOL/api-una-copia.yaml" >/dev/null
kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
for i in 1 2 3 4 5; do dentro $NS curl -s --retry 8 --retry-all-errors --retry-delay 1 -m 5 -o /dev/null -X POST --data "nota $i" http://bitacora-api/api/notas; done
igual "una sola lista" 1 "$(listas)"
kn rollout restart deploy/bitacora-api >/dev/null; kn rollout status deploy/bitacora-api --timeout=180s >/dev/null
tras="$(dentro $NS curl -s --retry 8 --retry-all-errors --retry-delay 1 -m 5 http://bitacora-api/api/notas | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["notas"]))')"
igual "las 5 notas sobreviven al reinicio" 5 "$tras"
iniciar_medidor $NS http://bitacora-api/api/info; sleep 3
kn rollout restart deploy/bitacora-api >/dev/null; kn rollout status deploy/bitacora-api --timeout=180s >/dev/null; sleep 4
read -r total errores <<< "$(resultados_medidor $NS)"
corte="$(kn logs medidor | awk '$2!="200"{ if(!i)i=$1; f=$1 } END{ print (i? f-i+1 : 0) }')"
detener_medidor $NS
registrar "paso6_recreate: peticiones=$total errores=$errores corte_aprox_s=$corte (5 peticiones por segundo)"
afirmar "hay corte en cada despliegue con Recreate ($errores errores)" test "$errores" -gt 0

titulo "Paso 7: PostgreSQL 17 como StatefulSet"
kn create secret generic db --from-literal=password="$(openssl rand -hex 12)" >/dev/null
k apply -n $NS -f "$SOL/postgres.yaml" >/dev/null
kn rollout status statefulset/db --timeout=240s >/dev/null
listo_db db-0
registrar "paso7: $(kn exec db-0 -- postgres --version)"
psql_db "CREATE DATABASE bitacora" postgres >/dev/null
psql_db "CREATE TABLE notas(id serial primary key, texto text)" >/dev/null
psql_db "INSERT INTO notas(texto) VALUES ('uno'),('dos'),('tres')" >/dev/null
igual "tres filas" 3 "$(psql_db 'SELECT count(*) FROM notas')"

titulo "Paso 8: borrar el pod y borrar el StatefulSet"
uid_antes="$(kn get pod db-0 -o jsonpath='{.metadata.uid}')"
kn delete pod db-0 --wait >/dev/null
kn rollout status statefulset/db --timeout=240s >/dev/null
listo_db db-0
igual "mismo nombre db-0, pod nuevo" yes "$([ "$(kn get pod db-0 -o jsonpath='{.metadata.uid}')" != "$uid_antes" ] && echo yes || echo no)"
igual "las filas siguen" 3 "$(psql_db 'SELECT count(*) FROM notas')"
kn delete statefulset db --wait >/dev/null
registrar "paso8: tras borrar el StatefulSet -> $(kn get pvc datos-db-0 --no-headers | awk '{print $1, $2}')"
afirmar "la petición datos-db-0 sigue ahí" kn get pvc datos-db-0
k apply -n $NS -f "$SOL/postgres.yaml" >/dev/null
kn rollout status statefulset/db --timeout=240s >/dev/null
listo_db db-0
igual "al recrearlo, los datos reaparecen" 3 "$(psql_db 'SELECT count(*) FROM notas')"

titulo "Paso 9: escalar a dos"
kn scale statefulset db --replicas=2 >/dev/null
kn rollout status statefulset/db --timeout=240s >/dev/null
listo_db db-1
psql_db "INSERT INTO notas(texto) VALUES ('solo en db-0')" >/dev/null
en0="$(psql_db "SELECT count(*) FROM notas WHERE texto='solo en db-0'")"
en1="$(kn exec db-1 -- psql -U postgres -d bitacora -At -c "SELECT count(*) FROM notas" 2>&1 | grep -m1 'does not exist' || echo 0)"
registrar "paso9: en db-0 la fila aparece ($en0); en db-1: '$en1' (db-1 es otra base, sin replicación: ni siquiera tiene la base bitacora)"
igual "db-0 la tiene" 1 "$en0"
afirmar "db-1 no la tiene" sh -c "echo '$en1' | grep -qE 'does not exist|^0$'"
kn scale statefulset db --replicas=1 >/dev/null

titulo "Paso 10: respaldo y restauración"
k apply -n $NS -f "$SOL/respaldo.yaml" >/dev/null
kn create job respaldo-manual --from=cronjob/respaldo >/dev/null
kn wait --for=condition=complete job/respaldo-manual --timeout=180s >/dev/null
kn logs job/respaldo-manual | sed 's/^/     /'
k apply -n $NS -f "$SOL/restauracion.yaml" >/dev/null
kn wait --for=condition=Ready pod/restauracion --timeout=180s >/dev/null
listo_db restauracion
igual "la base de restauración está vacía" 0 "$(kn exec restauracion -- psql -U postgres -At -c "SELECT count(*) FROM pg_database WHERE datname='bitacora'")"
kn exec restauracion -- psql -U postgres -At -c "CREATE DATABASE bitacora" >/dev/null
kn exec restauracion -- sh -c 'psql -U postgres -d bitacora -q -v ON_ERROR_STOP=1 -f /respaldos/ultimo.sql' >/dev/null
filas="$(kn exec restauracion -- psql -U postgres -d bitacora -At -c 'SELECT count(*) FROM notas')"
registrar "paso10: filas restauradas = $filas (db-0 tiene $(psql_db 'SELECT count(*) FROM notas'))"
igual "se restauraron las 4 filas en una base vacía" 4 "$filas"
afirmar "el CronJob es válido y programado" sh -c "kubectl --kubeconfig '$KUBECONFIG' --context kind-$CLUSTER -n $NS get cronjob respaldo -o jsonpath='{.spec.schedule}' | grep -q '0 \* \* \* \*'"

titulo "Criterio: ninguna contraseña en los archivos"
afirmar "no hay password con valor en la solución" sh -c "! grep -rEi 'password: *[a-z0-9]|value: *.?(local|postgres)' '$SOL' | grep -v 'POSTGRES_PASSWORD\$'"

titulo "Limpieza"
k delete ns $NS --wait >/dev/null && ok "espacio de nombres borrado"
terminar "Kubernetes 06"
