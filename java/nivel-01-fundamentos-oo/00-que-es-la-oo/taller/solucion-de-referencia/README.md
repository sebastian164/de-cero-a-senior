# Taller 0 — Un pedido que se defiende solo — solución de referencia

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Esta es una solución posible, no la única. Tu entrega va en `taller/solucion/`, no aquí.

## Cómo ejecutarla

Necesitas JDK 21. Desde esta carpeta:

```bash
javac -d out *.java
java -cp out Main
java -cp out Pruebas
```

Pruebas: 15, 0 fallos. Al terminar, borra las carpetas `out`.
El diagnóstico se ejecuta con `javac -d out2 diagnostico/DemoViolaciones.java ../inicio/*.java && java -cp out2 DemoViolaciones`.

## Qué hay que entender

Lee [ANALISIS.md](ANALISIS.md): explica cada decisión y la compara con el código de inicio.
