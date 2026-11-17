# Kafka 01: El registro de eventos: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Los diez pasos del taller de consola (temas, claves, grupos, retención y compactación), cada uno con su comprobación.

Análisis y decisiones: [ANALISIS.md](ANALISIS.md).

## Requisitos

Docker.

## Cómo ejecutarla y probarla

```bash
bash ejecutar.sh
```

Tarda unos cinco minutos (hay esperas reales por la retención). Levanta el entorno del curso (`kafka-y-sagas/entorno/compose.yaml`, sin puerto publicado) y el `compose.yaml` de esta carpeta, y los baja al terminar. La salida cruda de cada paso queda en `salida/`. Termina con código 0 solo si todas las comprobaciones pasan.
