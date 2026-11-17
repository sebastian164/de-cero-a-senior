#!/usr/bin/env python3
"""Paso 1: junto a cada criterio de la especificación, anota qué prueba lo vigila. Uso: anotar.py especificacion.md"""
import re, sys
p = sys.argv[1]
texto = "\n".join(open(p, encoding="utf-8").read().splitlines()) + "\n"
bloques = re.split(r"(?m)^(?=\d+\. )", texto)
res = []
for b in bloques:
    m = re.match(r"(\d+)\. ", b)
    if m and "## Casos límite" not in b.split("\n\n")[0] and "\n## " not in b:
        n = m.group(1)
        res.append(b.rstrip("\n") + f"\n   _Prueba: `@criterio-{n}` en `pruebas/src/test/java/etiquetas.feature`_\n")
    elif m:
        # el último criterio comparte bloque con las secciones siguientes
        antes, despues = b.split("\n## ", 1)
        n = m.group(1)
        res.append(antes.rstrip("\n") + f"\n   _Prueba: `@criterio-{n}` en `pruebas/src/test/java/etiquetas.feature`_\n\n## " + despues)
    else:
        res.append(b)
open(p, "w", encoding="utf-8").write("".join(res))
