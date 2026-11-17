# Soluciones de referencia de los proyectos de la ruta Java

> **Intenta cada proyecto antes de abrir su solución.** Ver la respuesta primero convierte el proyecto en lectura y no en práctica. Estas soluciones son *una* implementación mínima y verificada de cada enunciado, no *la* solución: lo que se evalúa en tu entrega (diseño, decisiones, historial de commits, defensa) no está aquí y no se puede copiar.

Los enunciados están en [proyectos/README.md](../README.md). Cada carpeta es autónoma: compila, pasa sus pruebas y arranca desde su propia carpeta, sin depender de las demás ni del resto del curso. Cada una trae su README (qué requisitos del enunciado cubre y dónde, cómo probarla y usarla) y su registro de decisiones.

| # | Proyecto | Tecnología | Necesita | Solución |
|---|---|---|---|---|
| 1 | Gestor de biblioteca en consola | Java puro | JDK 21 y Maven | [01-gestor-de-biblioteca](01-gestor-de-biblioteca/README.md) |
| 2 | Motor de precios y promociones | Java puro | JDK 21 y Maven | [02-motor-de-precios](02-motor-de-precios/README.md) |
| 3 | Billetera digital con pruebas | Java, JUnit, AssertJ | JDK 21 y Maven | [03-billetera-digital](03-billetera-digital/README.md) |
| 4 | API de reservas con arquitectura hexagonal | Spring Boot, PostgreSQL, Testcontainers | además Docker | [04-api-de-reservas-hexagonal](04-api-de-reservas-hexagonal/README.md) |
| 5 | Tienda como monolito modular con eventos | Spring Boot, Modulith, PostgreSQL, Kafka | además Docker | [05-tienda-monolito-modular](05-tienda-monolito-modular/README.md) |
| 6 | Plataforma desplegada y defendida | Docker, kind, OpenTofu, Grafana, Prometheus, Tempo | además kind, kubectl y OpenTofu | [06-plataforma-final](06-plataforma-final/README.md) |

## Qué hay y qué falta, con honestidad

Las soluciones implementan el núcleo de cada enunciado y las pruebas lo demuestran. **No incluyen las extensiones** que los enunciados proponen (revistas, promociones como datos, conversión de moneda, segundo adaptador de entrada, extraer un módulo a un servicio), ni lo que solo puede ser tuyo: el historial de commits, la defensa y el documento de diseño del proyecto 6. Cada README lo dice en su apartado "Qué no cubre".

## Cómo se usan

Requieren JDK 21 y Maven. Los proyectos 4 a 6 usan además Docker: sus pruebas levantan PostgreSQL y Kafka con Testcontainers, y su `compose.yaml` levanta lo mismo (con los puertos estándar: 5432, 9092) para ejecutarlos a mano. Cada proyecto se prueba con:

```bash
cd 01-gestor-de-biblioteca   # o el que sea
mvn clean verify
```

Los proyectos 4 a 6 se levantan con `docker compose up -d --wait` y se bajan con `docker compose down -v`; el 6 además necesita un clúster kind (sus pasos están en su README). Levanta uno a la vez: comparten puertos estándar.
