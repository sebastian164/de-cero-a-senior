# Nivel 9 — Sistemas distribuidos

Requisito: Niveles 5, 7 y 8. Qué cambia cuando las partes del sistema se comunican por una red que puede fallar.

| # | Sesión |
|---|---|
| 01 | [Fundamentos de sistemas distribuidos y CAP](01-fundamentos-y-cap/) |
| 02 | [Comunicación síncrona: tiempos límite, reintentos e idempotencia](02-comunicacion-sincrona/) |
| 03 | [Mensajería y eventos](03-mensajeria-y-eventos/) |
| 04 | [Consistencia entre servicios: Outbox y sagas](04-consistencia-sagas-outbox/) |
| 05 | [Resiliencia](05-resiliencia/) |
| 06 | [Observabilidad distribuida y operación](06-observabilidad-distribuida/) |

El código de los fragmentos y los talleres se ejecutó con Spring Boot 4.1.1, Java 21, PostgreSQL 18.7, Apache Kafka 4.3.1, Resilience4j 2.4.0 y la pila `grafana/otel-lgtm` 0.40.0 (Collector, Tempo, Prometheus, Loki y Grafana). Los talleres requieren Docker para levantar brokers, bases y herramientas de observabilidad.
