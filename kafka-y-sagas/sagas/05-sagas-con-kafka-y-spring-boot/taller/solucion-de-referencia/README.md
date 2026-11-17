# Sagas 05: La saga sobre Kafka y Spring Boot: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Cuatro servicios reales (orquestador, Pagos, Inventario y Envíos) con Kafka y PostgreSQL.

Análisis y decisiones: [ANALISIS.md](ANALISIS.md).

## Requisitos

JDK 21, Maven y Docker. Las pruebas comparan `Transiciones` y `Estado` con los de `sagas/02-saga-orquestada/taller/solucion-de-referencia` (ruta relativa dentro de este repositorio).

## Cómo ejecutarla y probarla

Las pruebas hablan con un broker de Kafka y con PostgreSQL reales. `compose.pruebas.yaml` los levanta en los puertos 47092 (Kafka) y 47432 (PostgreSQL), para no chocar con tu entorno.

```bash
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml up -d --wait
mvn test
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml down -v
```

La prueba `ContenedoresTest` crea además sus propios contenedores con Testcontainers, así que Docker debe estar encendido durante `mvn test`.

