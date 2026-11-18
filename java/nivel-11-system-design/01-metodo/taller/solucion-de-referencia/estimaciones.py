#!/usr/bin/env python3
"""Aritmética de las estimaciones del taller 11-01. Cada cifra de ANALISIS.md sale de aquí. Ejecuta: python3 estimaciones.py"""
DIA = 86_400

def seccion(t): print(f"\n== {t}")

seccion("1. Turnos de una entidad pública, 300 sedes")
sedes, turnos_dia_sede, horas = 300, 400, 8       # supuesto: 400 turnos por sede y día
turnos_dia = sedes * turnos_dia_sede
prom = turnos_dia / (horas * 3600)
pico = prom * 5
pantallas_poll = sedes * 10 / 5                    # 10 pantallas por sede consultando cada 5 s
print(f"turnos/día {turnos_dia:,}; promedio {prom:.1f}/s en la jornada; pico (x5) {pico:.1f}/s; lecturas de pantallas {pantallas_poll:.0f}/s")
fila_bytes = 200; anual = turnos_dia * 250 * fila_bytes / 1e9
print(f"almacenamiento: {turnos_dia:,} x 250 días x {fila_bytes} B = {anual:.1f} GB/año")
assert pico < 100 and anual < 100   # cabe en una base relacional con réplica

seccion("2. Plataforma de encuestas, pico en una hora")
respuestas, ventana = 2_000_000, 3600              # supuesto: 2 millones de respuestas en una hora, concentradas
prom = respuestas / ventana; pico = prom * 4       # el primer cuarto de hora concentra el pico
print(f"respuestas/hora {respuestas:,}: promedio {prom:.0f}/s; pico (x4) {pico:.0f}/s")
bytes_resp = 2_000; print(f"ingreso {pico * bytes_resp / 1e6:.1f} MB/s; almacenamiento {respuestas * bytes_resp / 1e9:.1f} GB por encuesta de ese tamaño")
workers = pico / 500                               # 500 inserciones/s por consumidor con lotes
print(f"con una cola y consumidores que insertan por lotes (500/s c/u): {workers:.0f} consumidores para absorber el pico sin cola creciente")
assert 2000 < pico < 3000

seccion("3. Documentos con control de acceso")
usuarios, docs_por_usuario_mes, mb = 50_000, 20, 2
subidas_mes = usuarios * docs_por_usuario_mes
almacen_anual_tb = subidas_mes * 12 * mb / 1e6
lecturas_s = subidas_mes * 10 / (30 * DIA)         # 10 lecturas por documento y mes
print(f"subidas/mes {subidas_mes:,}; almacenamiento {almacen_anual_tb:.1f} TB/año; lecturas {lecturas_s:.1f}/s en promedio")
print(f"ancho de banda de lectura a pico x10: {lecturas_s*10*mb:.0f} MB/s")
assert almacen_anual_tb < 50
print("\nOK")
