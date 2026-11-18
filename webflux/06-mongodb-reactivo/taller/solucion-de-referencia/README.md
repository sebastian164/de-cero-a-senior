# Solución de referencia: taller 06

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Necesita JDK 21, Maven y Docker (MongoDB como conjunto de réplicas, puerto 27018).

Levanta el entorno del curso (desde la raíz del repositorio):

```bash
docker compose -f webflux/entorno/compose.yaml up -d --wait
```

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21; export PATH=$JAVA_HOME/bin:$PATH   # o tu JDK 21
cd webflux/06-mongodb-reactivo/taller/solucion-de-referencia
mvn test                                  # 4 pruebas contra el MongoDB real
mvn -q package -DskipTests
java -jar target/t06-solucion-1.0.0.jar --modo=servicio    # confirmador | notificador | sondeo | servicio
```

El razonamiento, las mediciones y las decisiones están en [ANALISIS.md](ANALISIS.md).
