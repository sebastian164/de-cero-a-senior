#!/usr/bin/env bash
# Paso 6 del taller 03: w:1 frente a w:"majority" cuando cae el primario antes de replicar.
set -uo pipefail
cd "$(dirname "$0")"
P="docker compose -p ${PROYECTO:-nosql-rs3} -f replicaset3.yaml"
OUT=salida/replicaset3.txt; mkdir -p salida; : > "$OUT"
FALLOS=0
say() { echo "$*" | tee -a "$OUT"; }
sh1() { $P exec -T "$1" mongosh --quiet --eval "$2" 2>&1; }
espera_primario() { # $1 = nodo por el que preguntar
  for i in $(seq 1 60); do
    p=$(sh1 "$1" 'const h = db.hello(); h.isWritablePrimary ? "SI" : "NO"' | tail -1)
    [ "$p" = "SI" ] && return 0; sleep 1; done; return 1; }
arriba() {
  $P down -v >/dev/null 2>&1; $P up -d >/dev/null 2>&1 || { say "no levantó"; exit 1; }
  for i in $(seq 1 30); do sh1 mongo1 'db.hello().ok' | grep -q 1 && break; sleep 1; done
  sh1 mongo1 'rs.initiate({_id:"rs3", members:[{_id:0,host:"mongo1:27017",priority:2},{_id:1,host:"mongo2:27017",priority:1},{_id:2,host:"mongo3:27017",priority:1}]}).ok' >/dev/null
  espera_primario mongo1 || { say "sin primario"; exit 1; }
  for i in $(seq 1 60); do n=$(sh1 mongo1 'rs.status().members.filter(m=>m.stateStr=="SECONDARY").length' | tail -1); [ "$n" = "2" ] && break; sleep 1; done
}
c() { $P ps -aq "$1"; }
RED=${PROYECTO:-nosql-rs3}_default
aislar() { for n in mongo2 mongo3; do docker network disconnect $RED "$(c $n)" >/dev/null; done; }
reconectar() { for n in mongo2 mongo3; do docker network connect --alias $n $RED "$(c $n)" >/dev/null; done; }
existe() { sh1 "$1" "db.getSiblingDB('t03').c.countDocuments({_id:'$2'})" | tail -1; }
buscar_primario() { for n in mongo2 mongo3; do [ "$(sh1 $n 'db.hello().isWritablePrimary' | tail -1)" = "true" ] && echo $n && return; done; }
nuevo_primario() { for i in $(seq 1 60); do p=$(buscar_primario); [ -n "$p" ] && echo "$p" && return; sleep 1; done; }

say "== A. w:1, los secundarios sin red, no reciben la escritura, el primario cae"
arriba
say "miembros: $(sh1 mongo1 'rs.status().members.map(m=>m.name+" "+m.stateStr).join(", ")' | tail -1)"
aislar
R=$(sh1 mongo1 'JSON.stringify(db.getSiblingDB("t03").c.insertOne({_id:"a"},{writeConcern:{w:1}}))' | tail -1)
say "escritura w:1 con los secundarios aislados de la red: $R"
docker kill "$(c mongo1)" >/dev/null
reconectar
NP=$(nuevo_primario); say "nuevo primario: $NP"
A1=$(existe "$NP" a); say "el documento 'a' en el nuevo primario: $A1 (confirmado al cliente con w:1, y perdido)"
[ "$A1" = "0" ] || FALLOS=$((FALLOS+1))
docker start "$(c mongo1)" >/dev/null; sleep 12
A2=$(existe mongo1 a); say "el documento 'a' en el antiguo primario tras volver (rollback): $A2"
[ "$A2" = "0" ] || FALLOS=$((FALLOS+1))

say "== B. w:majority, los secundarios sin red, no reciben la escritura, el primario cae"
arriba
aislar
R=$(sh1 mongo1 'try { JSON.stringify(db.getSiblingDB("t03").c.insertOne({_id:"b"},{writeConcern:{w:"majority",wtimeout:3000}})) } catch (e) { "ERROR " + e.codeName + ": " + e.message }' | tail -1)
say "escritura w:majority con los secundarios aislados de la red: $R"
case "$R" in ERROR*) ;; *) FALLOS=$((FALLOS+1));; esac
docker kill "$(c mongo1)" >/dev/null
reconectar
NP=$(nuevo_primario); say "nuevo primario: $NP"
B1=$(existe "$NP" b); say "el documento 'b' en el nuevo primario: $B1 (al cliente se le dijo que falló, y no está: coherente)"
[ "$B1" = "0" ] || FALLOS=$((FALLOS+1))

say "== C. w:majority con los tres nodos sanos, el primario cae después de confirmar"
arriba
R=$(sh1 mongo1 'JSON.stringify(db.getSiblingDB("t03").c.insertOne({_id:"c"},{writeConcern:{w:"majority",wtimeout:5000}}))' | tail -1)
say "escritura w:majority: $R"
docker kill "$(c mongo1)" >/dev/null
NP=$(nuevo_primario); say "nuevo primario: $NP"
C1=$(existe "$NP" c); say "el documento 'c' en el nuevo primario: $C1 (confirmada por la mayoría: sobrevive)"
[ "$C1" = "1" ] || FALLOS=$((FALLOS+1))
$P down -v >/dev/null 2>&1
say "fallos: $FALLOS"
exit $FALLOS
