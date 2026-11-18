# Solución de referencia: taller 01 (método de diseño de sistemas)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Qué es

Los tres sistemas del taller resueltos con los seis pasos del método (requisitos, estimación, API, modelo de datos, arquitectura y compromisos) en [`ANALISIS.md`](ANALISIS.md). Es un documento: la parte ejecutable es la aritmética de las estimaciones.

## Cómo ejecutarla y probarla

Necesitas Python 3. Desde esta carpeta:

```bash
python3 estimaciones.py
```

Imprime las cifras de cada sistema (turnos por segundo, almacenamiento, consumidores necesarios, ancho de banda) y termina con `OK` y código 0 si todas sus comprobaciones (`assert`) pasan. No levanta nada ni deja archivos.

Las decisiones, los supuestos y los compromisos de cada sistema están en [`ANALISIS.md`](ANALISIS.md).
