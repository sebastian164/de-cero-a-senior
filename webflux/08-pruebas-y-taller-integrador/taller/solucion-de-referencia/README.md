# Solución de referencia: taller 08

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Necesita JDK 21, Maven y Docker (las pruebas usan Testcontainers y descargan `postgres:18.7`). El dominio de la tienda (el del módulo hexagonal) no se copia: el `pom.xml` lo toma de `hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src` por ruta relativa, así que ejecútala dentro del repositorio del curso.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21; export PATH=$JAVA_HOME/bin:$PATH   # o tu JDK 21
cd webflux/08-pruebas-y-taller-integrador/taller/solucion-de-referencia
mvn test                                  # 26 pruebas
```

Para ejecutarla a mano (aplicación en el 8080; inventario y pagos simulados en 8081 y 8082):

Levanta el entorno del curso (desde la raíz del repositorio):

```bash
docker compose -f webflux/entorno/compose.yaml up -d --wait
```

```bash
cd webflux/08-pruebas-y-taller-integrador/taller/solucion-de-referencia
docker compose -f ../../../entorno/compose.yaml exec -T postgres psql -U banco -d banco -c "create database pedidos08"
mvn -q package -DskipTests
java -jar target/t08-solucion-1.0.0.jar &            # crea el esquema al arrancar
docker compose -f ../../../entorno/compose.yaml exec -T postgres psql -q -U banco -d pedidos08 < datos.sql
java -cp target/test-classes pedidos.ServiciosSimulados inventario 8081 300 &
java -cp target/test-classes pedidos.ServiciosSimulados pagos 8082 300 &
sleep 5
curl -X POST localhost:8080/pedidos/m1/confirmacion   # responde 200 con estado CONFIRMADO
```

El razonamiento, las mediciones y las decisiones están en [ANALISIS.md](ANALISIS.md).
