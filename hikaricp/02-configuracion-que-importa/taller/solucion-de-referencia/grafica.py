"""Dibuja en SVG peticiones por segundo frente a maximum-pool-size, una línea por serie del barrido."""
import sys

filas = [l.split() for l in open(sys.argv[1], encoding="utf-8") if l.strip()]
series = {}
for etiqueta, tamano, rps, _ in filas:
    series.setdefault(etiqueta, []).append((int(tamano), float(rps)))
ancho, alto, margen = 640, 360, 50
maximo = max(r for s in series.values() for _, r in s) * 1.1
x = lambda t: margen + (t / 28) * (ancho - 2 * margen)
y = lambda r: alto - margen - (r / maximo) * (alto - 2 * margen)
colores = ["#2563eb", "#dc2626", "#16a34a", "#9333ea", "#ea580c"]
partes = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{ancho}" height="{alto}" font-family="sans-serif" font-size="12">',
          f'<rect width="{ancho}" height="{alto}" fill="white"/>',
          f'<line x1="{margen}" y1="{alto-margen}" x2="{ancho-margen}" y2="{alto-margen}" stroke="black"/>',
          f'<line x1="{margen}" y1="{margen}" x2="{margen}" y2="{alto-margen}" stroke="black"/>',
          f'<text x="{ancho/2}" y="{alto-12}" text-anchor="middle">maximum-pool-size</text>',
          f'<text x="14" y="{alto/2}" transform="rotate(-90 14 {alto/2})" text-anchor="middle">peticiones/s</text>']
for t in (2, 5, 10, 20, 28):
    partes.append(f'<text x="{x(t)}" y="{alto-margen+16}" text-anchor="middle">{t}</text>')
for i, (etiqueta, puntos) in enumerate(sorted(series.items())):
    c = colores[i % len(colores)]
    camino = " ".join(f"{x(t):.1f},{y(r):.1f}" for t, r in sorted(puntos))
    partes.append(f'<polyline points="{camino}" fill="none" stroke="{c}" stroke-width="2"/>')
    for t, r in puntos:
        partes.append(f'<circle cx="{x(t):.1f}" cy="{y(r):.1f}" r="3" fill="{c}"/>')
    partes.append(f'<text x="{margen+10}" y="{margen+16*i}" fill="{c}">{etiqueta}</text>')
partes.append("</svg>")
open(sys.argv[2], "w", encoding="utf-8").write("\n".join(partes))
