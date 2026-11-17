# Kafka 08: Dos hexágonos que hablan por Kafka: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

El servicio de pedidos del módulo hexagonal más un hexágono de inventario, unidos por Kafka.

Análisis y decisiones: [ANALISIS.md](ANALISIS.md).

## Requisitos

JDK 21, Maven y Docker. Compila contra la solución de referencia de `hexagonal/08-pruebas-y-limites` (ruta relativa dentro de este repositorio).

## Cómo ejecutarla y probarla

Las pruebas hablan con un broker de Kafka y con PostgreSQL reales. `compose.pruebas.yaml` los levanta en los puertos 47092 (Kafka) y 47432 (PostgreSQL), para no chocar con tu entorno.

```bash
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml up -d --wait
mvn test
docker compose -p pub-kafka-y-sagas -f compose.pruebas.yaml down -v
```

