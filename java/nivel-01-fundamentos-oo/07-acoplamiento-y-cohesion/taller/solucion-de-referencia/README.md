# Taller — Auditoría de acoplamiento y cohesión — solución de referencia

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Esta es una solución posible, no la única. Tu entrega va en `taller/solucion/`, no aquí.

## Cómo ejecutarla

Necesitas JDK 21. Desde esta carpeta:

```bash
cd acoplamiento-temporal/antes
java Main.java
cd ../despues
javac -d out $(find . -name '*.java')
java -cp out Main
java -cp out Pruebas
```

Pruebas de `despues`: 2, 0 fallos. Al terminar, borra las carpetas `out`.
Los dos archivos de `acoplamiento-temporal/no-compila/` deben fallar al compilarse junto con `despues/correo/`; el detalle está en `ANALISIS.md`.

## Qué hay que entender

Lee [ANALISIS.md](ANALISIS.md): explica cada decisión y la compara con el código de inicio.
