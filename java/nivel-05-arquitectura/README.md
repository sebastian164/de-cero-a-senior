# Nivel 5 — Arquitectura de software

Requisito: Niveles 1 a 4. La arquitectura es diseño orientado a objetos y SOLID aplicados un nivel más arriba: a paquetes, módulos y sistemas.

| # | Lección |
|---|---|
| 01 | [Separación de responsabilidades](01-separacion-de-responsabilidades/) |
| 02 | [Arquitectura en capas](02-arquitectura-en-capas/) |
| 03 | [MVC](03-mvc/) |
| 04 | [Inversión de dependencias aplicada a arquitectura](04-inversion-de-dependencias-en-arquitectura/) |
| 05 | [Clean Architecture](05-clean-architecture/) |
| 06 | [Arquitectura hexagonal](06-arquitectura-hexagonal/) |
| 07 | [Onion Architecture](07-onion-architecture/) |
| 08 | [Domain-Driven Design](08-domain-driven-design/) |
| 09 | [Modularidad](09-modularidad/) |
| 10 | [Monolitos frente a microservicios](10-monolitos-vs-microservicios/) |
| 11 | [Patrones arquitectónicos](11-patrones-arquitectonicos/) |
| 12 | [Taller integrador](12-taller-integrador/) |

## Para practicar a fondo

La lección 06 da el panorama de la arquitectura hexagonal. El módulo de [arquitectura hexagonal con Java](../../hexagonal/) la construye en ocho sesiones, con un banco de ejemplo que se compila y se ejecuta, y es la base de los talleres de Kafka, sagas y HikariCP.

## Sobre el orden

Separación de responsabilidades e inversión de dependencias van antes de Clean, hexagonal y Onion porque esas tres arquitecturas son aplicaciones de las mismas dos ideas.

## Sobre el código

Los fragmentos con Spring son ilustrativos y no se compilaron al preparar el curso. En este nivel pesan más las decisiones que el código: cada taller pide justificar, comparar alternativas y declarar costos.
