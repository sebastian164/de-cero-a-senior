#!/usr/bin/env python3
"""Taller nivel-05/04, punto 2: revisa en la salida de `next build` que cada ruta de la tienda quedó con la estrategia de la tabla.
Uso: comprobar-estrategias.py mediciones/build-completo.txt"""
import re, sys

ESPERADO = [
  ('/n5/04-renderizado/inicio', '○', None, 'Estática'),
  ('/n5/04-renderizado/categoria/bebidas', '○', '1m', 'Estática con regeneración por tiempo (1 min)'),
  ('/n5/04-renderizado/producto/1', '○', '30d', 'Estática con regeneración bajo demanda (etiqueta; vida máxima)'),
  ('/n5/04-renderizado/carrito', '◐', None, 'Cáscara estática con hueco dinámico (cookies)'),
  ('/n5/04-renderizado/busqueda', '◐', None, 'Dinámica con transmisión (searchParams)'),
  ('/n5/04-renderizado/mi-cuenta', '◐', None, 'Cáscara + hueco dinámico por usuario'),
  ('/n5/04-renderizado/lenta/bloqueante', 'ƒ', None, 'Dinámica sin transmisión (instant = false)'),
]
texto = open(sys.argv[1], encoding='utf-8').read()
fallos = 0
for ruta, simbolo, revalidacion, que in ESPERADO:
    m = re.search(r'^[├└│ ─┬┌]*\s*([○◐ƒ●])\s+' + re.escape(ruta) + r'(?:[ \t]+(\S+)[ \t]+(\S+))?[ \t]*$', texto, re.M)
    if not m:
        print(f'FALTA  {ruta}: no aparece en la salida de la construcción'); fallos += 1; continue
    ok = m.group(1) == simbolo and (revalidacion is None or m.group(2) == revalidacion)
    print(('OK     ' if ok else 'FALLA  ') + f'{ruta:45} {m.group(1)} {m.group(2) or "":4} {que}')
    fallos += 0 if ok else 1
sys.exit(1 if fallos else 0)
