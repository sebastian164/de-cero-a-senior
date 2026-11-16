# Taller integrador de SOLID — Clínica veterinaria — solución de referencia

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Esta es una solución posible, no la única. Tu entrega va en `taller/solucion/`, no aquí.

## Cómo ejecutarla

Necesitas JDK 21. Desde esta carpeta:

```bash
java 1-sin-diseno/ClinicaVeterinaria.java
cd 2-rediseno
javac -d out *.java
java -cp out Main
cd ../3-con-cambios
javac -d out *.java
java -cp out Main
java -cp out Pruebas
```

Pruebas de `3-con-cambios`: 32, 0 fallos. Al terminar, borra las carpetas `out`.

## Qué hay que entender

Lee [ANALISIS.md](ANALISIS.md): explica cada decisión y la compara con el código de inicio.
