# Solución de referencia: taller 03

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Necesita JDK 21 y Maven. No usa Docker.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21; export PATH=$JAVA_HOME/bin:$PATH   # o tu JDK 21
cd webflux/03-contrapresion-y-schedulers/taller/solucion-de-referencia
mvn test                                          # 8 pruebas
mvn test -Dpagos=apurado -Dtest=BloqueoEnElBucleTest   # el estado "antes": BlockHound hace fallar la prueba
mvn -q compile exec:exec                          # las mediciones del ANALISIS
```

El razonamiento, las mediciones y las decisiones están en [ANALISIS.md](ANALISIS.md).
