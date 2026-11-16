# Taller 01 — Nombres significativos: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Trabaja en `../solucion/` y vuelve aquí para comparar.

## Cómo ejecutarla

Requiere JDK 21. Desde esta carpeta:

```bash
java Main.java | diff - legado/salida-esperada.txt && echo "Misma salida que el legado"
```

`legado/Main.java` es el código original del taller; `Main.java` es el mismo código con los nombres nuevos. Si `diff` no imprime nada, el comportamiento no cambió.

El razonamiento, punto por punto, está en [ANALISIS.md](ANALISIS.md).
