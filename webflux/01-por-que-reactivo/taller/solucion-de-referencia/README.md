# Solución de referencia: taller 01

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Necesita JDK 21 y Maven. No usa Docker.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21; export PATH=$JAVA_HOME/bin:$PATH   # o tu JDK 21
cd webflux/01-por-que-reactivo/taller/solucion-de-referencia
mvn test                       # pruebas
mvn -q compile exec:java       # las mediciones del ANALISIS
```

El razonamiento, las mediciones y las decisiones están en [ANALISIS.md](ANALISIS.md).
