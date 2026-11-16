# Taller LSP — solución de referencia

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Esta es una solución posible, no la única. Tu entrega va en `taller/solucion/`, no aquí.

## Cómo ejecutarla

Necesitas JDK 21. Desde esta carpeta:

```bash
java a-clasico/Main.java
java a-alternativa/Main.java
cd b-documentos
javac -d out *.java
java -cp out Main
java -cp out Pruebas
```

Pruebas de `b-documentos`: 5, 0 fallos. Al terminar, borra las carpetas `out`.
`doble-de-prueba/` se compila contra `hikaricp/06-el-pool-en-un-servicio-hexagonal/ejemplo/src/ConexionesDePrueba.java` de este mismo repositorio; el comando exacto está en `ANALISIS.md`. Los archivos de `b-documentos/no-compila/` deben fallar al compilarse.

## Qué hay que entender

Lee [ANALISIS.md](ANALISIS.md): explica cada decisión y la compara con el código de inicio.
