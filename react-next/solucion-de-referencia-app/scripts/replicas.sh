#!/usr/bin/env bash
# Taller nivel-05/04, punto 5: la aplicación en un contenedor con salida independiente y dos réplicas; qué pasa con la caché.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p mediciones
IMG=ref-react-next-tienda:1
A=ref-react-next-replica-a; B=ref-react-next-replica-b
docker rm -f $A $B >/dev/null 2>&1 || true
scripts/construir-imagen.sh $IMG
docker run -d --name $A -e INSTANCIA=A -e SECRETO_WEBHOOK=secreto-compartido-del-webhook -p 49551:3000 $IMG >/dev/null
docker run -d --name $B -e INSTANCIA=B -e SECRETO_WEBHOOK=secreto-compartido-del-webhook -p 49552:3000 $IMG >/dev/null
trap 'docker rm -f $A $B >/dev/null 2>&1 || true' EXIT
for p in 49551 49552; do
  for i in $(seq 1 60); do curl -sf -o /dev/null "http://127.0.0.1:$p/n5/04-renderizado/inicio" && break; sleep 1; done
done
hora() { curl -s "http://127.0.0.1:$1/n5/04-renderizado/hora-cacheada" | python3 -c "import re,sys; t=sys.stdin.read(); print(re.search(r'data-testid=\"hora\"[^>]*>([^<]*)<',t).group(1))"; }
H0A=$(hora 49551); H0B=$(hora 49552)
curl -s -XPOST -H 'x-secreto-webhook: secreto-compartido-del-webhook' -H 'content-type: application/json' -d '{"etiqueta":"hora"}' http://127.0.0.1:49551/api/n5-02/revalidar >/dev/null
H1A=$(hora 49551); sleep 1; H2A=$(hora 49551); H2B=$(hora 49552)
python3 - "$H0A" "$H0B" "$H1A" "$H2A" "$H2B" <<'PY'
import json,sys
a0,b0,a1,a2,b2=sys.argv[1:]
r={"inicio":{"A":a0,"B":b0},"tras_revalidar_en_A":{"A_primera_lectura":a1,"A_segunda_lectura":a2,"B":b2},
   "A_cambio":a2!=a0,"B_quedo_viejo":b2==b0}
json.dump(r,open('mediciones/n5-04-replicas.json','w'),indent=2,ensure_ascii=False)
print(json.dumps(r,indent=2,ensure_ascii=False))
assert r["A_cambio"] and r["B_quedo_viejo"], "se esperaba que la réplica A cambiara y la B siguiera con el dato viejo"
PY
echo "OK: la invalidación solo alcanzó a la réplica que la recibió"
