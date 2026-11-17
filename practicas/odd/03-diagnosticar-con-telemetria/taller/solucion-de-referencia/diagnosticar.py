#!/usr/bin/env python3
"""Diagnostica un fallo de Bitácora usando SOLO la interfaz de observabilidad (Grafana: Prometheus y Tempo).
No lee el código, la configuración ni los contenedores. Sigue el método de la sesión:
  1. síntoma (¿qué indicador se degrada?), 2. dónde (¿qué ruta, qué copia?), 3. por qué (¿qué dicen los tramos?).
Uso: diagnosticar.py <puerto de Grafana> <ventana en segundos>
Escribe una línea JSON con el diagnóstico y las consultas que lo sostienen."""
import base64, json, sys, time, urllib.parse, urllib.request

PUERTO, VENTANA = sys.argv[1], int(sys.argv[2])
G = f"http://localhost:{PUERTO}/api/datasources/proxy/uid"
AUTH = "Basic " + base64.b64encode(b"admin:admin").decode()
consultas = []


def pedir(url):
    r = urllib.request.Request(url, headers={"Authorization": AUTH})
    with urllib.request.urlopen(r, timeout=30) as f:
        return json.load(f)


def prom(expr):
    consultas.append("PromQL " + expr)
    d = pedir(f"{G}/prometheus/api/v1/query?" + urllib.parse.urlencode({"query": expr}))
    return d["data"]["result"]


def tempo(q, limite=30):
    consultas.append("TraceQL " + q)
    # Tempo tarda un rato en poder buscar lo recién llegado: se reintenta unas veces.
    for _ in range(8):
        d = pedir(f"{G}/tempo/api/search?" + urllib.parse.urlencode({"q": q, "limit": limite, "start": int(time.time()) - VENTANA - 5, "end": int(time.time()) + 5}))
        if d.get("traces"):
            return d["traces"]
        time.sleep(5)
    return []


def tramos(trace_id, nombre):
    t = pedir(f"{G}/tempo/api/traces/{trace_id}")
    for b in t.get("batches", []):
        for ss in b["scopeSpans"]:
            for s in ss["spans"]:
                if s["name"] == nombre:
                    atr = {a["key"]: list(a["value"].values())[0] for a in s.get("attributes", [])}
                    eventos = [{a["key"]: list(a["value"].values())[0] for a in e.get("attributes", [])} for e in s.get("events", [])]
                    dur = (int(s["endTimeUnixNano"]) - int(s["startTimeUnixNano"])) / 1e6
                    yield atr, eventos, dur


def por(expr_total, etiquetas):
    return {tuple(r["metric"].get(e, "") for e in etiquetas): float(r["value"][1]) for r in prom(expr_total)}


W = f"[{VENTANA}s]"
import os
SERVICIO = os.environ.get("SERVICIO", "bitacora-api")
SERV = f'service_name="{SERVICIO}"'
res = {"diagnostico": "sin síntoma", "causa": "", "consultas": consultas}

# 1. SÍNTOMA. Proporción de 5xx, de 4xx y p95 de duración, por ruta y método.
tot = por(f"sum by (ruta, metodo) (increase(bitacora_peticiones_total{{{SERV}}}{W}))", ["ruta", "metodo"])
e5 = por(f'sum by (ruta, metodo) (increase(bitacora_peticiones_total{{{SERV},clase_estado="5xx"}}{W}))', ["ruta", "metodo"])
e4 = por(f'sum by (ruta, metodo) (increase(bitacora_peticiones_total{{{SERV},clase_estado="4xx"}}{W}))', ["ruta", "metodo"])
p95 = por(f"histogram_quantile(0.95, sum by (le, ruta, metodo) (increase(bitacora_duracion_milliseconds_bucket{{{SERV}}}{W})))", ["ruta", "metodo"])
POST, GET = ("/api/notas", "POST"), ("/api/notas", "GET")
r5 = e5.get(POST, 0) / tot[POST] if tot.get(POST) else 0
r4 = e4.get(POST, 0) / tot[POST] if tot.get(POST) else 0
res["sintoma"] = {"POST_5xx": round(r5, 3), "POST_4xx": round(r4, 3), "POST_p95_ms": round(p95.get(POST, 0)), "GET_p95_ms": round(p95.get(GET, 0))}


def reparto(clase):
    # 2. DÓNDE. ¿Los errores se reparten entre las copias o se concentran en una?
    por_copia = por(f'sum by (service_instance_id) (increase(bitacora_peticiones_total{{{SERV},ruta="/api/notas",metodo="POST",clase_estado="{clase}"}}{W}))', ["service_instance_id"])
    total = sum(por_copia.values())
    return {k[0]: v / total for k, v in por_copia.items()} if total else {}


if r5 > 0.05:
    share = reparto("5xx")
    concentrada = max(share.values()) >= 0.9 if share else False
    # Qué parte de las escrituras de cada copia falla (si una copia falla siempre, el problema es de esa copia).
    escrituras = por(f'sum by (service_instance_id) (increase(bitacora_peticiones_total{{{SERV},ruta="/api/notas",metodo="POST"}}{W}))', ["service_instance_id"])
    fallidas = por(f'sum by (service_instance_id) (increase(bitacora_peticiones_total{{{SERV},ruta="/api/notas",metodo="POST",clase_estado="5xx"}}{W}))', ["service_instance_id"])
    tasa = {k[0]: round(fallidas.get(k, 0) / v, 2) for k, v in escrituras.items() if v}
    # 3. POR QUÉ. Los tramos de escritura con error dicen qué número de escritura falló y con qué tipo de excepción.
    secuencias, tipos = [], set()
    for t in tempo(f'{{ resource.service.name = "{SERVICIO}" && name = "notas.escribir" && status = error }}', 12)[:12]:
        for atr, eventos, _ in tramos(t["traceID"], "notas.escribir"):
            if "bitacora.escritura.secuencia" in atr:
                secuencias.append(int(atr["bitacora.escritura.secuencia"]))
            tipos |= {e.get("exception.type", "") for e in eventos}
    res["evidencia"] = {"reparto_5xx_por_copia": {k: round(v, 2) for k, v in share.items()}, "fraccion_de_escrituras_fallidas_por_copia": tasa,
                        "secuencias_de_los_tramos_con_error": sorted(secuencias)[:12], "tipos_de_excepcion": sorted(tipos)}
    sistema_de_archivos = any(t.startswith("java.nio.file.") for t in tipos)
    if concentrada and max(tasa.values(), default=0) >= 0.9 and sistema_de_archivos:
        res.update(diagnostico="disco_solo_lectura", causa="todas las escrituras de UNA copia fallan (100 %) y las otras no; la excepción es del sistema de archivos (" + ", ".join(sorted(tipos)) + "): esa copia no puede escribir en su disco")
    elif concentrada and max(tasa.values(), default=0) >= 0.9:
        res.update(diagnostico="una_copia_rota_sin_causa", causa="todas las escrituras de una copia fallan, pero la excepción no dice por qué: " + ", ".join(sorted(tipos)))
    elif not concentrada and 0.1 <= r5 <= 0.3 and len(secuencias) >= 3 and all(n % 5 == 0 for n in secuencias):
        res.update(diagnostico="errores_1_de_5", causa=f"el 5xx ({r5:.0%} de las escrituras) se reparte entre todas las copias y el número de escritura de cada tramo con error es siempre múltiplo de 5: falla una de cada cinco")
    else:
        res.update(diagnostico="errores_sin_causa", causa="hay 5xx pero ni el reparto ni los tramos los explican")
elif r4 > 0.05:
    share = reparto("4xx")
    res["evidencia"] = {"reparto_4xx_por_copia": {k: round(v, 2) for k, v in share.items()}}
    if share and max(share.values()) >= 0.9:
        res.update(diagnostico="clave_distinta", causa="todos los 4xx de guardar vienen de una sola copia: rechaza una clave que las otras aceptan (configuración distinta)")
    else:
        res.update(diagnostico="rechazos_sin_causa", causa="los rechazos se reparten entre las copias")
elif p95.get(POST, 0) > 1000:
    # ¿Qué distingue a las escrituras lentas de las rápidas? Se comparan las dos poblaciones por el atributo de longitud.
    def longitudes(consulta):
        con, sin = [], 0
        for t in tempo(consulta, 30)[:30]:
            for atr, _, dur in tramos(t["traceID"], "notas.escribir"):
                if "bitacora.nota.longitud" in atr:
                    con.append(int(atr["bitacora.nota.longitud"]))
                else:
                    sin += 1
        return con, sin
    lentas, sin_l = longitudes(f'{{ resource.service.name = "{SERVICIO}" && name = "notas.escribir" && duration > 1s }}')
    rapidas, sin_r = longitudes(f'{{ resource.service.name = "{SERVICIO}" && name = "notas.escribir" && duration < 200ms }}')
    res["evidencia"] = {"tramos_lentos": len(lentas) + sin_l, "tramos_rapidos": len(rapidas) + sin_r, "tramos_sin_atributo_de_longitud": sin_l + sin_r,
                        "longitud_minima_de_las_lentas": min(lentas) if lentas else None, "longitud_maxima_de_las_rapidas": max(rapidas) if rapidas else None}
    if not (lentas or sin_l):
        res.update(diagnostico="sin_evidencia", causa="guardar es lento pero ningún tramo notas.escribir lo explica")
    elif sin_l and not lentas:
        res.update(diagnostico="indeterminado_post_lento", causa="guardar es lento y el tiempo está en notas.escribir, pero el tramo no dice qué tienen en común las lentas: FALTA el atributo bitacora.nota.longitud")
    elif rapidas and min(lentas) > max(rapidas):
        res.update(diagnostico="notas_largas", causa=f"las escrituras lentas son todas de notas de {min(lentas)} caracteres o más y ninguna rápida pasa de {max(rapidas)}: solo tardan las notas largas")
    else:
        res.update(diagnostico="post_lento_sin_causa", causa="las lentas y las rápidas tienen longitudes mezcladas")
elif p95.get(GET, 0) > 500:
    # Todos los tramos de lectura (rápidos y lentos): ¿la duración depende de cuántas notas hay?
    for intento in range(3):
        puntos, sin_atributo = [], 0
        for t in tempo(f'{{ resource.service.name = "{SERVICIO}" && name = "notas.leer" }}', 40)[:40]:
            for atr, _, dur in tramos(t["traceID"], "notas.leer"):
                if "bitacora.notas.total" in atr:
                    puntos.append((int(atr["bitacora.notas.total"]), dur))
                else:
                    sin_atributo += 1
        if len({n for n, _ in puntos}) >= 3 or sin_atributo:
            break
        time.sleep(8)   # pocas lecturas todavía: se vuelve a mirar cuando haya llegado más tráfico
    res["evidencia"] = {"tramos_notas.leer": len(puntos) + sin_atributo, "con_atributo_total_de_notas": len(puntos), "totales_distintos": len({n for n, _ in puntos})}
    if sin_atributo and not puntos:
        res.update(diagnostico="indeterminado_lectura_lenta", causa="leer notas es lento y el tiempo está en notas.leer, pero no se ve de qué depende: FALTA el atributo bitacora.notas.total")
    elif len({n for n, _ in puntos}) >= 3:
        xs, ys = [n for n, _ in puntos], [d for _, d in puntos]
        # La pendiente se calcula con los tramos que no llegaron al tope de 1,5 s (donde todavía crece con las notas).
        bajo = [(x, y) for x, y in puntos if y < 1400]
        pend = 0.0
        if len({x for x, _ in bajo}) >= 2:
            bx, by = sum(x for x, _ in bajo) / len(bajo), sum(y for _, y in bajo) / len(bajo)
            den = sum((x - bx) ** 2 for x, _ in bajo)
            pend = sum((x - bx) * (y - by) for x, y in bajo) / den if den else 0
        def mediana(v):
            v = sorted(v); n = len(v)
            return v[n // 2] if n % 2 else (v[n // 2 - 1] + v[n // 2]) / 2
        def rangos(v):  # rangos con empates promediados (la duración se aplana en el tope de 1,5 s)
            orden = sorted(range(len(v)), key=lambda i: v[i]); rg = [0.0] * len(v); i = 0
            while i < len(v):
                j = i
                while j + 1 < len(v) and v[orden[j + 1]] == v[orden[i]]:
                    j += 1
                for k in range(i, j + 1):
                    rg[orden[k]] = (i + j) / 2 + 1
                i = j + 1
            return rg
        rx, ry = rangos(xs), rangos(ys)
        mrx, mry = sum(rx) / len(rx), sum(ry) / len(ry)
        sxx2 = sum((a - mrx) ** 2 for a in rx); syy2 = sum((b - mry) ** 2 for b in ry); sxy2 = sum((a - mrx) * (b - mry) for a, b in zip(rx, ry))
        r = sxy2 / (sxx2 * syy2) ** 0.5 if sxx2 and syy2 else 0   # correlación de rangos (Spearman): la duración sube con las notas aunque se aplane en el tope
        res["evidencia"].update(correlacion_duracion_vs_notas=round(r, 2), ms_por_nota_en_la_zona_lineal=round(pend, 1),
                                duracion_mediana_con_pocas_notas_ms=round(mediana([ys[i] for i in range(len(xs)) if xs[i] == min(xs)])), duracion_mediana_con_muchas_notas_ms=round(mediana([ys[i] for i in range(len(xs)) if xs[i] == max(xs)])))
        if r >= 0.8:
            res.update(diagnostico="lectura_lenta", causa=f"la duración de notas.leer crece con bitacora.notas.total (correlación de rangos {r:.2f}" + (f", unos {pend:.0f} ms por nota" if pend > 1 else "") + f"): leer es lento en proporción a las notas guardadas")
        else:
            res.update(diagnostico="lectura_lenta_sin_causa", causa=f"notas.leer es lento pero no depende de la cantidad de notas (correlación de rangos {r:.2f})")
    else:
        res.update(diagnostico="lectura_lenta_sin_contraste", causa="notas.leer es lento, pero todas las copias tienen la misma cantidad de notas y no hay con qué comparar")
print(json.dumps(res, ensure_ascii=False))
