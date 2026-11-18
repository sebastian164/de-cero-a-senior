#!/usr/bin/env python3
"""Cálculos del taller 12-04: estimación de tres valores (PERT) y sesgo de estimaciones pasadas.
Los datos de las estimaciones pasadas son de un caso ficticio, declarados como tales en ANALISIS.md."""
import math, sys

# Parte 4: la funcionalidad "estado del pedido con eventos" (días-persona): optimista, probable, pesimista
partes = [
    ("Migración compatible hacia atrás", 0.5, 1, 2),
    ("Modelo y transiciones de estado con pruebas", 2, 3, 6),
    ("Publicar evento en Kafka (Outbox)", 2, 4, 9),
    ("Endpoint de consulta y autorización", 1, 2, 4),
    ("Pruebas de integración y de carga", 2, 3, 6),
    ("Despliegue, tableros y runbook", 1, 2, 4),
    ("Revisión de código y correcciones", 1, 2, 3),
]
esp = var = 0.0
print(f"{'Parte':50s} {'O':>5s} {'P':>5s} {'Pe':>5s} {'Esperado':>9s} {'Desv.':>6s}")
for n, o, p, pe in partes:
    e = (o + 4 * p + pe) / 6; d = (pe - o) / 6
    esp += e; var += d * d
    print(f"{n:50s} {o:5.1f} {p:5.1f} {pe:5.1f} {e:9.2f} {d:6.2f}")
sd = math.sqrt(var)
print(f"\nTotal esperado: {esp:.1f} días-persona; desviación {sd:.1f}; ~50 % de confianza: {esp:.1f}; ~84 %: {esp + sd:.1f}; ~97,5 %: {esp + 2 * sd:.1f}")
suma_prob = sum(p for _, _, p, _ in partes)
print(f"La suma de los valores 'probables' es {suma_prob:.1f}: ignorar la cola derecha subestima {esp - suma_prob:.1f} días")
resultados = {"esperado": esp, "sd": sd, "p84": esp + sd, "p975": esp + 2 * sd}

# Parte 5: tres estimaciones pasadas (caso ficticio) frente a lo real
pasadas = [("Importador de archivos", 5, 9), ("Reporte mensual", 8, 11), ("Integración con la pasarela", 10, 19)]
print("\nEstimación frente a realidad (caso ficticio)")
razones = []
for n, est, real in pasadas:
    r = real / est; razones.append(r)
    print(f"{n:32s} estimado {est:3d} d  real {real:3d} d  razón {r:.2f}")
media_geom = math.exp(sum(math.log(r) for r in razones) / len(razones))
print(f"Razón media geométrica real/estimado: {media_geom:.2f} -> sesgo sistemático: subestimo ~{(media_geom - 1) * 100:.0f} %")
assert all(r > 1 for r in razones) and 1.4 < media_geom < 1.8
assert abs(esp - 18.6) < 0.1 and esp > suma_prob
print("OK")
