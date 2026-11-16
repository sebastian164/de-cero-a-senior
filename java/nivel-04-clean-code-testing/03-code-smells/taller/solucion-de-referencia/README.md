# Taller 03 — Auditoría de code smells: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Trabaja en `../solucion/` y vuelve aquí para comparar.

## Cómo ejecutarla

Requiere JDK 21. Desde esta carpeta:

```bash
java Main.java | diff - ../../../01-nombres/taller/solucion-de-referencia/legado/salida-esperada.txt && echo "Misma salida que el legado"
```

Compara contra la salida del módulo heredado del taller 01, así que esa carpeta debe existir en el repositorio.

El razonamiento, punto por punto, está en [ANALISIS.md](ANALISIS.md).
