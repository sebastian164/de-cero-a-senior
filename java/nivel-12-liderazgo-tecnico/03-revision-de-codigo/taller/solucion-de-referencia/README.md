# Solución de referencia: taller 12-03 (revisión de código)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Qué es

La revisión de tres pull requests reales y públicos de [google/gson](https://github.com/google/gson), en [`ANALISIS.md`](ANALISIS.md). Los diffs revisados están en `prs/` (`gson-3137.diff`, `gson-3144.diff` y `gson-3157.diff`), tal como se leyeron, para que puedas contrastar cada observación con el código. No hay nada que levantar ni ejecutar.

## Cómo comprobarla

No tiene pruebas automáticas. Para comprobar que la carpeta está completa:

```bash
test -s ANALISIS.md && test -s prs/gson-3137.diff && test -s prs/gson-3144.diff && test -s prs/gson-3157.diff && echo OK
```

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
