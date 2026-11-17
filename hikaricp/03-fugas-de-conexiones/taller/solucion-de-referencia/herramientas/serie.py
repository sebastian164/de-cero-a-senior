"""Dibuja en SVG una o varias series temporales "segundo valor" (archivos .tsv) con su etiqueta.

Uso: python3 -I serie.py salida.svg "título del eje y" etiqueta1=archivo1.tsv [etiqueta2=archivo2.tsv ...]
"""
import sys

salida, eje = sys.argv[1], sys.argv[2]
series = []
for arg in sys.argv[3:]:
    etiqueta, ruta = arg.split("=", 1)
    puntos = [tuple(float(v) for v in l.split()[:2]) for l in open(ruta, encoding="utf-8") if l.strip()]
    series.append((etiqueta, puntos))
ancho, alto, m = 720, 360, 50
tmax = max(t for _, p in series for t, _ in p) or 1
vmax = max(max(v for _, p in series for _, v in p), 1) * 1.15
x = lambda t: m + t / tmax * (ancho - 2 * m)
y = lambda v: alto - m - v / vmax * (alto - 2 * m)
colores = ["#2563eb", "#dc2626", "#16a34a", "#9333ea", "#ea580c"]
partes = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{ancho}" height="{alto}" font-family="sans-serif" font-size="12">',
          f'<rect width="{ancho}" height="{alto}" fill="white"/>',
          f'<line x1="{m}" y1="{alto-m}" x2="{ancho-m}" y2="{alto-m}" stroke="black"/>',
          f'<line x1="{m}" y1="{m}" x2="{m}" y2="{alto-m}" stroke="black"/>',
          f'<text x="{ancho/2}" y="{alto-12}" text-anchor="middle">segundos</text>',
          f'<text x="14" y="{alto/2}" transform="rotate(-90 14 {alto/2})" text-anchor="middle">{eje}</text>']
for v in range(0, int(vmax) + 1, max(1, int(vmax) // 6)):
    partes.append(f'<text x="{m-6}" y="{y(v)+4:.1f}" text-anchor="end">{v}</text>')
for t in range(0, int(tmax) + 1, max(1, int(tmax) // 8)):
    partes.append(f'<text x="{x(t):.1f}" y="{alto-m+16}" text-anchor="middle">{t}</text>')
for i, (etiqueta, puntos) in enumerate(series):
    c = colores[i % len(colores)]
    partes.append(f'<polyline points="{" ".join(f"{x(t):.1f},{y(v):.1f}" for t, v in puntos)}" fill="none" stroke="{c}" stroke-width="2"/>')
    partes.append(f'<text x="{m+10}" y="{m+16*i}" fill="{c}">{etiqueta}</text>')
partes.append("</svg>")
open(salida, "w", encoding="utf-8").write("\n".join(partes))
