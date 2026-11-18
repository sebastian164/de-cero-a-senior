#!/usr/bin/env bash
# Las cuatro métricas de entrega (DORA) calculadas sobre un proyecto real de código abierto con versiones publicadas:
# google/gson, con el historial fijado en un commit para que el resultado se pueda repetir. Solo se necesitan los
# commits y las etiquetas (clon parcial, sin archivos). Si ya hay clon en .cache, no usa la red.
#
# Definiciones (los "proxies" que hay que declarar porque no se tiene el entorno de producción de ese proyecto):
#   despliegue            = una versión publicada (etiqueta con número de versión) en la rama principal
#   1. frecuencia         = versiones publicadas por mes entre la primera y la última de la ventana
#   2. tiempo de entrega  = por cada commit, desde su fecha hasta la fecha de la primera versión que lo contiene
#   3. tasa de fallos     = versiones X.Y.Z seguidas, en menos de 30 días, por una corrección X.Y.(Z+1) (proxy de "fallo en producción")
#   4. recuperación       = desde la versión con fallo hasta su corrección publicada
set -euo pipefail
URL=${DORA_URL:-https://github.com/google/gson.git}
PIN=${DORA_SHA:-e67037d17f94415179066c8301fee4a610d9515e}
DIR=${DORA_CACHE:-$HOME/.cache/curso-de-cero-a-senior/dora-gson.git}
if [ ! -d "$DIR" ]; then git clone -q --bare --filter=tree:0 "$URL" "$DIR"; fi
git -C "$DIR" cat-file -e "$PIN^{commit}" 2>/dev/null || git -C "$DIR" fetch -q origin
python3 - "$DIR" "$PIN" "$URL" <<'PY'
import re, subprocess, sys, statistics
repo, pin, url = sys.argv[1:4]
def git(*a): return subprocess.run(["git", "-C", repo, *a], capture_output=True, text=True, check=True).stdout
principal = {l.split()[0]: int(l.split()[1]) for l in git("log", "--first-parent", "--format=%H %ct", pin).splitlines()}
versiones = []
for etiqueta in git("tag").split():
    m = re.search(r"(\d+)\.(\d+)(?:\.(\d+))?$", etiqueta)
    if not m: continue
    sha = git("rev-parse", etiqueta + "^{commit}").strip()
    if sha in principal:
        versiones.append((principal[sha], tuple(int(x or 0) for x in m.groups()), etiqueta, sha))
versiones.sort()
if len(versiones) < 5: sys.exit("muy pocas versiones en la ventana")
meses = max((versiones[-1][0] - versiones[0][0]) / 86400 / 30.44, 1)
print(f"proyecto: {url} fijado en {pin[:10]}; {len(versiones)} versiones publicadas entre {versiones[0][2]} y {versiones[-1][2]} ({meses:.0f} meses)")
print(f"1. frecuencia de despliegue: {len(versiones) / meses:.1f} versiones por mes")
entrega = []
for (t0, _, _, anterior), (t1, _, _, actual) in zip(versiones, versiones[1:]):
    for l in git("rev-list", "--format=%ct", actual, "--not", anterior).splitlines():
        if l.isdigit() and int(l) <= t1: entrega.append((t1 - int(l)) / 86400)
entrega.sort()
print(f"2. tiempo de entrega (commit hasta la primera versión que lo contiene, {len(entrega)} commits): mediana {statistics.median(entrega):.1f} días, p90 {entrega[int(.9 * len(entrega))]:.1f} días")
fallos, rec = 0, []
for (t0, v0, e0, _), (t1, v1, e1, _) in zip(versiones, versiones[1:]):
    if v1[:2] == v0[:2] and v1[2] == v0[2] + 1 and (t1 - t0) < 30 * 86400:
        fallos += 1; rec.append((t1 - t0) / 86400)
print(f"3. porcentaje de versiones con fallo (seguidas de una corrección en menos de 30 días): {100 * fallos / len(versiones):.0f} % ({fallos} de {len(versiones)})")
print(f"4. tiempo de recuperación (de la versión con fallo a su corrección): mediana {statistics.median(rec) if rec else 0:.1f} días")
PY
