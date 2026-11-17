# Sagas 07: La tienda completa: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Siete servicios hexagonales con Kafka y PostgreSQL, simulacro de fallos y verificador de invariantes.

Análisis y decisiones: [ANALISIS.md](ANALISIS.md).

## Requisitos

JDK 21, Maven y Docker. Las pruebas comparan `Transiciones` y `Estado` con los de las sesiones 02 y 05 (rutas relativas dentro de este repositorio).

## Cómo ejecutarla y probarla

Las pruebas hablan con un broker de Kafka y con PostgreSQL reales. `compose.pruebas.yaml` los levanta en los puertos 47092 (Kafka) y 47432 (PostgreSQL), para no chocar con tu entorno.

```bash
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml up -d --wait
mvn test
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml down -v
```

### La tienda con una sola orden de Compose

`mvn test` omite `ComposeTest`. Para ejecutarlo hay que levantar antes los siete servicios en contenedores (puertos 47701, 47711 y 47743):

```bash
mvn package -DskipTests
docker compose -p pub-kafka-y-sagas-s07 -f compose/compose.yaml up -d --build --wait
mvn surefire:test -Dcompose.s07=true -Dtest=ComposeTest
docker compose -p pub-kafka-y-sagas-s07 -f compose/compose.yaml down -v
docker image rm tienda-integrador:1.0
```

