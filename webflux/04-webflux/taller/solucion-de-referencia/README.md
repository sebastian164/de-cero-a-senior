# Solución de referencia: taller 04

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Necesita JDK 21 y Maven. Las pruebas levantan sus propios servicios simulados: no usan Docker.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21; export PATH=$JAVA_HOME/bin:$PATH   # o tu JDK 21
cd webflux/04-webflux/taller/solucion-de-referencia
mvn test                                          # 9 pruebas
```

Para verla funcionar: la aplicación escucha en el 8080 y llama al inventario (8081) y a pagos (8082).

```bash
mvn -q -DskipTests package
java -cp target/classes pedidos.ServiciosSimulados inventario 8081 300 &
java -cp target/classes pedidos.ServiciosSimulados pagos 8082 300 &
java -jar target/t04-solucion-1.0.0.jar &
curl -X POST localhost:8080/pedidos/p1/confirmacion
curl -N localhost:8080/pedidos/p7/eventos
```

El razonamiento, las mediciones y las decisiones están en [ANALISIS.md](ANALISIS.md).
