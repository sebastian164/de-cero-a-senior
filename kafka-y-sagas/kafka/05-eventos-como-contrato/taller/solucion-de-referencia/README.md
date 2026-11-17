# Kafka 05: Eventos como contrato: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Esquemas JSON, evolución compatible y el registro de esquemas (Apicurio).

Análisis y decisiones: [ANALISIS.md](ANALISIS.md).

## Requisitos

JDK 21, Maven y Docker.

## Cómo ejecutarla y probarla

Las pruebas hablan con un broker de Kafka, PostgreSQL y el registro de esquemas reales. `compose.pruebas.yaml` los levanta en los puertos 47092 (Kafka), 47432 (PostgreSQL) y 47081 (registro de esquemas), para no chocar con tu entorno.

```bash
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml up -d --wait
mvn test
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml down -v
```

