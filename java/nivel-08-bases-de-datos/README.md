# Nivel 8 — Bases de datos

Requisito: Nivel 7. La mayor parte de los problemas de rendimiento y de consistencia de una aplicación empresarial están en su base de datos.

| # | Sesión |
|---|---|
| 01 | [Modelado relacional y normalización](01-modelado-relacional/) |
| 02 | [Índices y planes de ejecución](02-indices-y-planes/) |
| 03 | [Transacciones, ACID y niveles de aislamiento](03-transacciones-y-aislamiento/) |
| 04 | [Control de concurrencia: bloqueo optimista y pesimista](04-concurrencia-y-bloqueos/) |
| 05 | [Migraciones y evolución del esquema](05-migraciones/) |
| 06 | [NoSQL y caché](06-nosql-y-cache/) |

Los ejemplos usan PostgreSQL. El SQL, los fragmentos con Spring Data y los talleres se ejecutaron contra PostgreSQL 18.7 y Redis 8.10.3 con Spring Boot 4.1.1, Java 21 y Flyway 12.4.0 (el plan del ejemplo de la sesión 02 es la salida real de esa ejecución con 2 millones de filas; los tiempos exactos dependen de tu máquina y de la cantidad de datos).

Para practicar a fondo el pool de conexiones, que conecta todo este nivel con la aplicación, están los [seis talleres de HikariCP](../../hikaricp/).

Las transacciones y la concurrencia de las lecciones 03 y 04 se ven funcionando, con pruebas que provocan cada fallo, en las sesiones de [transacciones](../../hexagonal/04-transacciones/) y de [concurrencia y aislamiento](../../hexagonal/05-concurrencia-y-aislamiento/) del módulo de arquitectura hexagonal.
