#!/usr/bin/env python3
"""Estimación mensual de la arquitectura del taller 10-01 (us-east-1). Los precios unitarios son SUPUESTOS de referencia
(precios públicos de lista, aproximados); hay que confirmarlos en la calculadora del proveedor antes de decidir."""
HORAS = 730
P = {  # USD
    "fargate_vcpu_hora": 0.04048, "fargate_gb_hora": 0.004445,
    "alb_hora": 0.0225, "alb_lcu_hora": 0.008,
    "rds_t4g_micro_hora": 0.016, "rds_gb_mes": 0.115,
    "nat_hora": 0.045, "nat_gb": 0.045,
    "s3_gb_mes": 0.023, "salida_gb": 0.09,
}
def escenario(tareas=2, vcpu=0.5, gb=1, multi_az=True, nats=2, fargate_spot=False, datos_gb=50, s3_gb=100, salida_gb=200, lcu=1):
    c = {}
    f = tareas * (vcpu * P["fargate_vcpu_hora"] + gb * P["fargate_gb_hora"]) * HORAS
    c["contenedores (Fargate)"] = f * (0.3 if fargate_spot else 1)
    c["balanceador"] = (P["alb_hora"] + lcu * P["alb_lcu_hora"]) * HORAS
    c["base de datos"] = (P["rds_t4g_micro_hora"] * HORAS + datos_gb * P["rds_gb_mes"]) * (2 if multi_az else 1)
    c["NAT"] = nats * P["nat_hora"] * HORAS
    c["almacenamiento S3"] = s3_gb * P["s3_gb_mes"]
    c["tráfico de salida"] = salida_gb * P["salida_gb"]
    return c
def total(c): return sum(c.values())
base = escenario()
print("Base: 2 tareas, RDS Multi-AZ, 2 NAT, 200 GB de salida")
for k, v in base.items(): print(f"  {k:28s} {v:8.2f} USD/mes")
print(f"  {'TOTAL':28s} {total(base):8.2f} USD/mes")
opt1 = escenario(nats=1)
opt2 = escenario(nats=1, fargate_spot=True)
print(f"\nReducción 1: una sola NAT (menos redundancia: se acepta en pruebas, no en producción): {total(opt1):.2f} USD/mes (ahorra {total(base)-total(opt1):.2f})")
print(f"Reducción 2: además Fargate Spot (solo tolerante a interrupciones) : {total(opt2):.2f} USD/mes (ahorra {total(base)-total(opt2):.2f})")
pruebas = escenario(tareas=1, multi_az=False, nats=1)
print(f"Ambiente de pruebas (1 tarea, RDS simple, 1 NAT)                   : {total(pruebas):.2f} USD/mes")
print(f"  si se apaga fuera de horario laboral (12 h x 5 días/semana) el cómputo baja a {pruebas['contenedores (Fargate)'] * 12 * 5 / 168:.2f} USD/mes")
assert 150 < total(base) < 250 and total(opt1) < total(base) and total(opt2) < total(opt1)
print("OK")
