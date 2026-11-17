# Kafka 02: Productores: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

`ProductorPedidos` y `Lote`, medidos con un clúster de tres brokers: réplicas sincronizadas, `acks`, idempotencia y qué se pierde al matar el proceso.

Análisis y decisiones: [ANALISIS.md](ANALISIS.md).

## Requisitos

JDK 21, Maven y Docker.

## Cómo ejecutarla y probarla

Las pruebas hablan con un broker de Kafka real. `compose.pruebas.yaml` lo levanta en el puerto 47092, para no chocar con tu entorno.

```bash
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml up -d --wait
mvn test
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml down -v
```

Las pruebas de la Parte A y del paso 10 necesitan además el clúster de tres brokers del curso, con los puertos 47291 a 47293 (sin esa propiedad se omiten):

```bash
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml up -d --wait
docker compose -p pub-kafka-y-sagas-kc -f ../../../../entorno/compose.cluster.yaml -f compose.cluster.pruebas.yaml up -d --wait
mvn test -Dkafka.cluster=localhost:47291,localhost:47292,localhost:47293
docker compose -p pub-kafka-y-sagas-kc -f ../../../../entorno/compose.cluster.yaml -f compose.cluster.pruebas.yaml down -v
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml down -v
```

