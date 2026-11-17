"""El panel del taller 05: cada segundo anota, de cada instancia, lo que el curso pone en el panel (métricas del pool,
disponibilidad) y, aparte, lo que se le añade en el segundo intento (duración por extremo, CPU de la base).
Uso: python3 panel.py salida.jsonl segundos puerto:registro [puerto:registro ...]   (se detiene al cumplirse los segundos)."""
import json, os, subprocess, sys, threading, time, urllib.parse, urllib.request

salida, segundos = sys.argv[1], int(sys.argv[2])
instancias = [a.split(":", 1) for a in sys.argv[3:]]
URIS = ["/lectura/{id}", "/escritura/{id}", "/externo/{id}", "/historial/{cliente}", "/fuga/{id}"]
cpu = {"v": None}


def pedir(url, espera=1.5):
    try:
        with urllib.request.urlopen(url, timeout=espera) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception:
        return 0, b""


def metrica(puerto, nombre, etiqueta=None):
    estado, cuerpo = pedir(f"http://localhost:{puerto}/actuator/metrics/{nombre}" + ("?tag=" + urllib.parse.quote(etiqueta, safe=":/") if etiqueta else ""))
    if estado != 200:
        return None
    return {m["statistic"]: m["value"] for m in json.loads(cuerpo)["measurements"]}


def vigilar_cpu():
    while True:
        try:
            texto = subprocess.run(["docker", "stats", "--no-stream", "--format", "{{.CPUPerc}}", os.environ.get("CONTENEDOR_DB", "hikaricp-taller-db-1")],
                                   capture_output=True, text=True, timeout=10).stdout.strip().rstrip("%")
            cpu["v"] = float(texto)
        except Exception:
            cpu["v"] = None
        time.sleep(1)


threading.Thread(target=vigilar_cpu, daemon=True).start()
inicio = time.time()
with open(salida, "w") as f:
    for s in range(segundos):
        t0 = time.time()
        fila = {"t": round(t0, 2), "cpu_base": cpu["v"], "instancias": {}}
        for puerto, registro in instancias:
            d = {}
            for clave, nombre in [("active", "hikaricp.connections.active"), ("idle", "hikaricp.connections.idle"),
                                  ("pending", "hikaricp.connections.pending"), ("max", "hikaricp.connections.max"),
                                  ("total", "hikaricp.connections")]:
                m = metrica(puerto, nombre)
                d[clave] = None if m is None else m["VALUE"]
            for clave, nombre in [("usage", "hikaricp.connections.usage"), ("acquire", "hikaricp.connections.acquire")]:
                m = metrica(puerto, nombre)
                d[clave] = None if m is None else [m["COUNT"], m["TOTAL_TIME"]]
            m = metrica(puerto, "hikaricp.connections.timeout")
            d["timeouts"] = None if m is None else m["COUNT"]
            d["readiness"] = pedir(f"http://localhost:{puerto}/actuator/health/readiness")[0]
            d["liveness"] = pedir(f"http://localhost:{puerto}/actuator/health/liveness")[0]
            d["log_bytes"] = os.path.getsize(registro) if os.path.exists(registro) else 0
            # lo que NO está en el panel de la primer intento:
            d["http"] = {}
            for uri in URIS:
                m = metrica(puerto, "http.server.requests", f"uri:{uri}")
                if m:
                    d["http"][uri] = [m["COUNT"], m["TOTAL_TIME"]]
            fila["instancias"][puerto] = d
        f.write(json.dumps(fila) + "\n"); f.flush()
        time.sleep(max(0, 1 - (time.time() - t0)))
