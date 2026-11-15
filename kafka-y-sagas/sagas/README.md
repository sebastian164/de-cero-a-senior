# Sagas

Seis talleres, en orden. Requisito: la parte de [Kafka](../kafka/), sobre todo las sesiones 04 (idempotencia) y 07 (outbox).

| # | Sesión | Taller |
|---|---|---|
| 01 | [Transacciones entre servicios](01-transacciones-entre-servicios/) | Diseñar la saga de compra y su primera versión en memoria |
| 02 | [Saga orquestada](02-saga-orquestada/) | Un orquestador que sobrevive a su propia caída |
| 03 | [Saga coreografiada](03-saga-coreografiada/) | La misma saga por eventos, y la comparación con números |
| 04 | [Cuando todo falla](04-cuando-todo-falla/) | Un broker hostil y una invariante que debe cumplirse siempre |
| 05 | [Sagas con Kafka y Spring Boot](05-sagas-con-kafka-y-spring-boot/) | Del modelo en memoria a Kafka y PostgreSQL |
| 06 | [Taller integrador](06-taller-integrador/) | La tienda completa, con simulacro y defensa |

Las sesiones 01 a 04 traen ejemplos en Java puro que se ejecutan con `java Main.java`: sirven para entender el patrón sin infraestructura. El detalle de qué se comprobó está en el [índice de la ruta](../README.md#estado-del-código).
