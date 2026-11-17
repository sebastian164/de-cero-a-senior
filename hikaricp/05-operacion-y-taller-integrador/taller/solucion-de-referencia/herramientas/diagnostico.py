"""Quien diagnostica, en el taller 05: solo mira lo grabado (el panel y los registros), nunca la provocación.
Uso: diagnostico.py serie(sin .jsonl) nivel   -> imprime una línea JSON con la etiqueta, los segundos desde la provocación y las pistas.
nivel 1 = el panel del curso (métricas del pool + disponibilidad + registros).
nivel 2 = nivel 1 + duración de las peticiones por extremo (http.server.requests) y CPU de la base: lo que "faltó" en el primer intento."""
import json, re, sys

VENTANA = 5            # segundos que mira hacia atrás para promediar
VENTANA_TEMPRANA = 20  # la alerta "pendientes > 0 durante un minuto" escalada al simulacro, que dura menos
LIMITE_BASE = 27       # max_connections 30 menos 3 reservadas a superusuarios

serie, nivel = sys.argv[1], int(sys.argv[2])
filas = [json.loads(l) for l in open(serie + ".jsonl")]
t0 = float(open(serie + ".t0").read())
i0 = next(i for i, f in enumerate(filas) if f["t"] >= t0)
puertos = list(filas[0]["instancias"])


def registro(puerto, desde, hasta):
    try:
        with open(f"{serie}.{puerto}.log", "rb") as f:
            f.seek(desde)
            return f.read(max(0, hasta - desde)).decode("utf-8", "replace")
    except OSError:
        return ""


def media(par_ahora, par_antes):
    """Promedio en la ventana (ms) de un temporizador [cuenta, tiempo_total en s]; None si no hubo muestras."""
    if not par_ahora or not par_antes:
        return None
    n = par_ahora[0] - par_antes[0]
    return None if n <= 0 else (par_ahora[1] - par_antes[1]) / n * 1000   # TOTAL_TIME viene en segundos


def evaluar(i):
    j = max(0, i - VENTANA)
    ahora, antes = filas[i]["instancias"], filas[j]["instancias"]
    texto = ""
    for p in puertos:
        texto += registro(p, filas[i0 - 1]["instancias"][p]["log_bytes"], ahora[p]["log_bytes"])
    leak = len(re.findall(r"Connection leak detection triggered", texto))
    devueltas = len(re.findall(r"Previously reported leaked connection .* was returned to the pool \(unleaked\)", texto))
    cortadas = len(re.findall(r"Failed to validate connection|terminating connection due to administrator command|This connection has been closed", texto))
    sin_hueco = len(re.findall(r"remaining connection slots are reserved", texto))
    rechazo = len(re.findall(r"Connection to [^ ]+ refused|Connection refused|the database system is (?:shutting down|starting up)|Failed to (?:validate|add) connection.*(?:refused|closed)", texto))
    arriba = [p for p in puertos if ahora[p]["max"] is not None]
    activas = {p: ahora[p]["active"] for p in arriba}
    maximos = {p: ahora[p]["max"] for p in arriba}
    pendientes = sum(ahora[p]["pending"] or 0 for p in arriba)
    timeouts = sum((ahora[p]["timeouts"] or 0) - ((antes[p]["timeouts"] or 0) if p in antes else 0) for p in arriba)
    uso = [media(ahora[p]["usage"], antes[p]["usage"]) for p in arriba]
    uso = [u for u in uso if u is not None]
    uso_medio = max(uso) if uso else None
    saturadas = sum(1 for p in arriba if activas[p] >= maximos[p])
    listo = [ahora[p]["readiness"] for p in puertos]
    conexiones_totales = sum(ahora[p]["total"] or 0 for p in arriba)
    pistas = {"leak": leak, "devueltas": devueltas, "cortadas": cortadas, "sin_hueco": sin_hueco, "rechazo": rechazo,
              "activas": sum(activas.values()), "max": sum(maximos.values()), "pendientes": pendientes, "timeouts": timeouts,
              "uso_ms": None if uso_medio is None else round(uso_medio), "readiness": listo, "conexiones_totales": conexiones_totales,
              "instancias_arriba": len(arriba)}
    if nivel >= 2:
        lentos = {}
        for p in arriba:
            for uri, par in ahora[p]["http"].items():
                m = media(par, antes[p]["http"].get(uri))
                if m is not None:
                    lentos.setdefault(uri, []).append(m)
        pistas["extremo_mas_lento"] = max(((max(v), u) for u, v in lentos.items()), default=(None, None))
        pistas["cpu_base"] = filas[i]["cpu_base"]
        # el extremo que más tiempo de conexión consume (aprox.): suma de las duraciones en la ventana
        consumo = {}
        for p in arriba:
            for uri, par in ahora[p]["http"].items():
                antes_uri = antes[p]["http"].get(uri, [0, 0])
                consumo[uri] = consumo.get(uri, 0) + par[1] - antes_uri[1]
        pistas["consumo_por_extremo"] = max(consumo.items(), key=lambda kv: kv[1])[0] if consumo else None
    return pistas, texto


estado = {"primer_aviso": None}


def etiqueta(i):
    p, texto = evaluar(i)
    if p["leak"] and estado["primer_aviso"] is None:
        estado["primer_aviso"] = i
    # 1. más instancias de las que caben: PostgreSQL rechaza por falta de huecos
    if p["sin_hueco"] and (p["conexiones_totales"] >= LIMITE_BASE - 3 or p["instancias_arriba"] < len(puertos)):
        return "demasiadas-instancias", p
    # 1b. la base está en su tope y algún pool no logra llenarse: la suma de hikaricp.connections llega al límite mientras alguna instancia queda por debajo de su máximo
    if p["conexiones_totales"] >= LIMITE_BASE and p["conexiones_totales"] < p["max"]:
        return "demasiadas-instancias", p
    # 2. la base no responde: la disponibilidad cae y el registro habla de rechazos, mientras la vida sigue
    if any(c == 503 for c in p["readiness"]) and p["rechazo"]:
        return "caida-de-la-base", p
    # 3. un aviso de fuga que nunca se devuelve
    if estado["primer_aviso"] is not None and i - estado["primer_aviso"] >= 4 and p["devueltas"] == 0:
        return "fuga", p
    # 4. conexiones cortadas desde fuera: el pool las encuentra rotas y las repone, sin cola sostenida
    if p["cortadas"] and p["pendientes"] == 0 and all(c == 200 for c in p["readiness"] if c):
        return "conexiones-cortadas", p
    # 4b. nivel 2: un extremo de consulta que se volvió varias veces más lento que antes y la CPU de la base disparada
    if nivel >= 2 and cpu_reciente(i) > 40 and lentitud_de_historial(i) >= 4:
        return "consulta-lenta", p
    # 4c. nivel 2: la tasa de peticiones se multiplicó varias veces y hay cola: la carga creció más que la capacidad
    if nivel >= 2 and (p["pendientes"] or p["timeouts"]) and crecimiento_de_peticiones(i) >= 3:
        return "capacidad", p
    # 5. uso largo con cola
    if p["uso_ms"] is not None and p["uso_ms"] >= 500 and (p["pendientes"] or p["timeouts"] or p["activas"] >= p["max"] * 0.8):
        if p["devueltas"]:
            return "retencion", p                       # los avisos de fuga se "desfugan": algo se queda con la conexión y luego la devuelve
        if nivel >= 2:
            extremo, _ = p["extremo_mas_lento"][1], None
            return ("consulta-lenta" if "historial" in str(extremo) and (p["cpu_base"] or 0) > 40 else "retencion"), p
        return "uso-largo-sin-explicacion", p
    # 5b. avisos de fuga que luego se devuelven, con cola: algo retiene la conexión mucho tiempo y la suelta
    if p["leak"] and p["devueltas"] and (p["pendientes"] or p["timeouts"]):
        # nivel 2: con la base a tope la causa está en la base, no en quien retiene; y se espera unos segundos a ver la duración por extremo
        if nivel >= 2 and (cpu_reciente(i) > 40 or filas[i]["t"] - t0 < 6):
            return None, p
        return "retencion", p
    # 6. falta de capacidad: la cola aparece con usos cortos y ningún aviso
    if p["uso_ms"] is not None and p["uso_ms"] < 150 and (p["pendientes"] or p["timeouts"]) and saturada_sostenida(i):
        return "capacidad", p
    return None, p


def cpu_reciente(i):
    """La CPU de la base más alta de los últimos segundos (docker stats llega con retraso)."""
    return max((filas[k]["cpu_base"] or 0) for k in range(max(0, i - VENTANA), i + 1))


def lentitud_de_historial(i):
    """Cuántas veces es más lenta, en la ventana, la consulta de historial que su media anterior a la provocación."""
    j = max(0, i - VENTANA)
    veces = 0
    for p in puertos:
        a, b, base = filas[i]["instancias"][p]["http"].get("/historial/{cliente}"), filas[j]["instancias"][p]["http"].get("/historial/{cliente}"), filas[i0 - 1]["instancias"][p]["http"].get("/historial/{cliente}")
        ahora, antes = media(a, b), (base[1] / base[0] * 1000 if base and base[0] else None)
        if ahora and antes:
            veces = max(veces, ahora / antes)
    return veces


def tasa(a, b, segundos):
    """Peticiones por segundo entre dos filas, sumadas todas las instancias y todos los extremos."""
    n = 0
    for p in puertos:
        for uri, par in filas[a]["instancias"][p]["http"].items():
            antes = filas[b]["instancias"][p]["http"].get(uri)
            n += par[0] - (antes[0] if antes else 0)
    return n / segundos


def crecimiento_de_peticiones(i):
    """Cuántas veces es mayor la tasa de peticiones de los últimos segundos que la de los diez anteriores a la provocación."""
    base = tasa(i0 - 1, max(0, i0 - 11), 10)
    j = max(0, i - VENTANA)
    return tasa(i, j, max(1, i - j)) / base if base > 0 else 0


def saturada_sostenida(i):
    n = 0
    for k in range(max(0, i - VENTANA), i + 1):
        f = filas[k]["instancias"]
        if any((f[p]["active"] or 0) >= (f[p]["max"] or 99) for p in puertos if f[p]["max"] is not None):
            n += 1
    return n >= 3


resultado = None
for i in range(i0 + 1, len(filas)):
    et, p = etiqueta(i)
    if et:
        resultado = {"etiqueta": et, "segundos": round(filas[i]["t"] - t0), "pistas": p}
        break
# las alertas del taller, evaluadas sobre la misma serie
alerta_temprana = alerta_grave = None
for i in range(i0, len(filas)):
    ahora = filas[i]["instancias"]
    if alerta_grave is None and i > i0 and sum(((ahora[p]["timeouts"] or 0) - (filas[i - 1]["instancias"][p]["timeouts"] or 0)) for p in puertos if ahora[p]["max"] is not None) > 0:
        alerta_grave = round(filas[i]["t"] - t0)
    if alerta_temprana is None and i - i0 >= VENTANA_TEMPRANA:
        if all(sum((filas[k]["instancias"][p]["pending"] or 0) for p in puertos if filas[k]["instancias"][p]["max"] is not None) > 0
               for k in range(i - VENTANA_TEMPRANA, i + 1)):
            alerta_temprana = round(filas[i]["t"] - t0)
print(json.dumps({"serie": serie.split("/")[-1], "nivel": nivel, "diagnostico": resultado,
                  "alerta_temprana_s": alerta_temprana, "alerta_grave_s": alerta_grave}, ensure_ascii=False))
