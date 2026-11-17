# Kafka 03: Consumidores y grupos: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Un consumidor de Kafka medido en grupo: reparto de particiones, caídas, confirmar antes o después de procesar, y ponerse al día.

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

