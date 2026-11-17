#!/usr/bin/env python3
"""Aprovisiona el tablero por la API de Grafana y comprueba que la consulta de CADA panel devuelve datos. Uso: importar_y_comprobar_tablero.py <grafana> <tablero.json> (hay que generar tráfico antes)."""
import base64, json, sys, urllib.request

base, ruta = sys.argv[1], sys.argv[2]
auth = "Basic " + base64.b64encode(b"admin:admin").decode()

def api(metodo, camino, cuerpo=None):
    req = urllib.request.Request(base + camino, method=metodo, data=json.dumps(cuerpo).encode() if cuerpo is not None else None,
                                 headers={"Authorization": auth, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read())

ds = next(d for d in api("GET", "/api/datasources") if d["type"] == "prometheus")
texto = open(ruta).read().replace("${DS}", ds["uid"])
tablero = json.loads(texto)
r = api("POST", "/api/dashboards/db", {"dashboard": tablero, "overwrite": True})
assert r["status"] == "success", r
guardado = api("GET", "/api/dashboards/uid/" + tablero["uid"])
assert len(guardado["dashboard"]["panels"]) == len(tablero["panels"])
print("tablero aprovisionado:", r["url"])
mal = 0
for p in tablero["panels"]:
    t = p["targets"][0]
    q = {"queries": [{"refId": "A", "datasource": {"type": "prometheus", "uid": ds["uid"]}, "expr": t["expr"], "instant": True, "range": False}], "from": "now-2m", "to": "now"}
    res = api("POST", "/api/ds/query", q)["results"]["A"]
    if "error" in res:
        print("  MAL ", p["title"], "->", res["error"]); mal += 1; continue
    valores = [v for f in res.get("frames", []) for col in f["data"]["values"][1:] for v in col if v is not None]
    ok = len(valores) > 0
    print("  " + ("OK  " if ok else "MAL "), p["title"], "->", valores[:1])
    mal += 0 if ok else 1
sys.exit(1 if mal else 0)
