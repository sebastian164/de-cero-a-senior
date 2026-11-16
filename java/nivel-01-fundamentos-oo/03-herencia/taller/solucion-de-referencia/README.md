# Taller — Herencia: figuras — solución de referencia

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Esta es una solución posible, no la única. Tu entrega va en `taller/solucion/`, no aquí.

## Cómo ejecutarla

Necesitas JDK 21. Desde esta carpeta:

```bash
cd 1-duplicado
javac -d out *.java
java -cp out Main
cd ..
cd 2-jerarquia
javac -d out *.java
java -cp out Main
java -cp out Pruebas
cd ..
cd properties
javac -d out *.java
java -cp out Main
```

Pruebas de `2-jerarquia`: 7, 0 fallos. Al terminar, borra las carpetas `out`.
Cada subcarpeta se compila por separado (por eso el `cd ..`).

## Qué hay que entender

Lee [ANALISIS.md](ANALISIS.md): explica cada decisión y la compara con el código de inicio.
