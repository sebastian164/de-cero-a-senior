# Kafka 06: Inventario con Spring Boot: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

El servicio de inventario hexagonal con política de errores, reintentos y cola de fallidos.

Análisis y decisiones: [ANALISIS.md](ANALISIS.md).

## Requisitos

JDK 21, Maven y Docker.

## Cómo ejecutarla y probarla

Las pruebas hablan con un broker de Kafka y con PostgreSQL reales. `compose.pruebas.yaml` los levanta en los puertos 47092 (Kafka) y 47432 (PostgreSQL), para no chocar con tu entorno.

```bash
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml up -d --wait
mvn test
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml down -v
```

La prueba `ContenedoresTest` crea además sus propios contenedores con Testcontainers (`apache/kafka:4.3.1` y `postgres:17`), así que Docker debe estar encendido durante `mvn test`.

