# Programación reactiva con Spring WebFlux

Servicios que esperan sin ocupar hilos: el modelo reactivo, Reactor, WebFlux y las bases de datos reactivas (R2DBC con PostgreSQL y MongoDB). Y la pregunta que importa más que la técnica: cuándo vale la pena y cuándo bastan los hilos virtuales de Java 21.

Esta ruta es para la [ruta Java](../java/), después del [Nivel 6 (Java avanzado)](../java/nivel-06-java-avanzado/), del [Nivel 8 (bases de datos)](../java/nivel-08-bases-de-datos/) y del módulo de [arquitectura hexagonal](../hexagonal/). Conviene haber hecho antes la de [HikariCP](../hikaricp/).

**Punto de partida:** el banco del módulo [hexagonal](../hexagonal/). Las sesiones 01 a 06 enseñan las piezas con ejemplos pequeños del mismo dominio (cuentas, movimientos, transferencias). La sesión 07 lleva el banco real a WebFlux y R2DBC, compilando su núcleo desde donde está, y discute qué le cuesta a una arquitectura hexagonal que la infraestructura deje de bloquear. Los talleres se hacen sobre tu servicio de pedidos.

Para el modelado de documentos en MongoDB (con el driver bloqueante) está el módulo [nosql](../nosql/). Aquí MongoDB aparece por lo que tiene de reactivo: el driver no bloqueante y los flujos de cambios.

## Orden

| # | Sesión | Qué terminas sabiendo hacer |
|---|---|---|
| 01 | [Por qué reactivo (y cuándo no)](01-por-que-reactivo/) | Medir cuándo un modelo no bloqueante gana, y compararlo con hilos virtuales |
| 02 | [Reactor: Mono, Flux y nada pasa hasta suscribir](02-reactor/) | Componer cadenas sin perder pasos y probarlas con StepVerifier y tiempo virtual |
| 03 | [Contrapresión, schedulers y el bucle de eventos](03-contrapresion-y-schedulers/) | Elegir una estrategia de contrapresión y encontrar bloqueos con BlockHound |
| 04 | [WebFlux: rutas, WebClient, errores y flujos](04-webflux/) | Exponer un servicio con WebFlux que llama a otros sin bloquear, con errores con nombre y SSE |
| 05 | [R2DBC con PostgreSQL](05-r2dbc-con-postgresql/) | Persistir sin bloquear, con transacciones reactivas, y explicar por qué JPA no encaja |
| 06 | [MongoDB reactivo y flujos de cambios](06-mongodb-reactivo/) | Enterarse de los cambios sin sondear, y reanudar tras una caída sin perder eventos |
| 07 | [El banco hexagonal reactivo](07-el-banco-hexagonal-reactivo/) | Decidir dónde va `Mono` en una arquitectura hexagonal y contar lo que cuesta |
| 08 | [Pruebas reactivas y taller integrador](08-pruebas-y-taller-integrador/) | Probar con StepVerifier, WebTestClient, Testcontainers y BlockHound, y defender el diseño con mediciones |

## Qué necesitas

- JDK 21 y Maven 3.9 o superior. Todos los ejemplos son proyectos Maven; los de las sesiones 01 a 03 no usan Spring, pero heredan de Spring Boot las versiones de Reactor.
- Docker, para el [entorno](entorno/) (sesiones 05 a 08) y para Testcontainers (sesión 08).
- `curl` y bash, para las mediciones. Las órdenes `time (...)` de los README son de bash.

Versiones fijadas en todos los `pom.xml` (vigentes en noviembre de 2026): Spring Boot 4.1.2, que trae Spring Framework 7.0.10, Reactor 2025.0.8 (reactor-core 3.8), Netty 4.2, r2dbc-postgresql 1.1.3, r2dbc-pool 1.0.2, el driver de MongoDB 5.8.1 y Testcontainers 2.0.5. BlockHound 1.0.18 no la fija Spring Boot y va con versión propia. Imágenes: `postgres:18.7` y `mongo:9.0.8`.

## Cómo es cada sesión

El ciclo de la [metodología](../METODOLOGIA.md): idea con analogía, lo esencial, un ejemplo que funciona pero está mal, su análisis, la versión corregida, errores comunes, taller con criterios de aceptación, cómo aparece en el trabajo real y preguntas senior.

Cada ejemplo tiene dos proyectos, `ejemplo/antes` y `ejemplo/despues`. El de "antes" funciona y responde bien a una prueba funcional; su problema se ve **midiendo**: con carga, con una espera en la base, con BlockHound o ejecutando dos veces. Cada README muestra la orden y la salida que se obtuvo al preparar el curso.

| Sesión | Cómo se ejecuta el ejemplo | Puertos |
|---|---|---|
| 01, 02 | `mvn -q compile exec:java` | — |
| 03 | `mvn -q compile exec:exec` (un JVM aparte, por BlockHound) | — |
| 04 | `mvn -q spring-boot:run`, más `java ServicioDeRiesgo.java` | 8090, 8091 y 8099 |
| 05 | `mvn -q spring-boot:run` | 8092 y 8093 |
| 06 | `mvn -q spring-boot:run` (un programa que mide y termina) | — |
| 07 | `mvn -q spring-boot:run` y `./ejemplo/bloqueo.sh` | 8094 y 8095 |
| 08 | `mvn test` | aleatorio |

## Cómo entregar

Una rama por taller (`taller/webflux-01`…), tu trabajo en `taller/solucion/` dentro de la sesión, un `ANALISIS.md` con lo que mediste y por qué, y un pull request contra `main`. No hay soluciones de referencia publicadas.

## Estado del código

| Qué | Cómo se comprobó al preparar el curso |
|---|---|
| Sesiones 01 a 03: seis programas en Java con Reactor | Compilados y ejecutados con JDK 21; la salida de cada uno es la que muestra su README. Usan tiempos reales: los milisegundos cambian un poco en cada ejecución |
| Pruebas de las sesiones 02 y 03 | `mvn test`: 5 y 2 pruebas, todas pasan |
| Sesión 04: dos aplicaciones WebFlux y el servicio de riesgo | Ejecutadas; las mediciones con `curl` y `xargs` del README se tomaron así |
| Sesiones 05 y 07: cuatro aplicaciones con PostgreSQL del entorno | Ejecutadas contra el entorno; las respuestas HTTP, la reversión de transacciones y las mediciones (incluido `bloqueo.sh`) son las del README |
| Sesión 06: dos programas con MongoDB del entorno | Ejecutados; latencias y conteos de comandos son los del README |
| Sesión 07: el núcleo del banco | Compilado desde [`hexagonal/proyecto`](../hexagonal/proyecto/) sin modificarlo |
| Sesión 08: pruebas | "Antes": 3 pruebas que pasan la primera vez y fallan la segunda, como dice el README. "Después": 11 pruebas (StepVerifier, WebTestClient con PostgreSQL en Testcontainers y BlockHound, arquitectura), todas pasan |
| [Entorno](entorno/) | Levantado con Docker 29 y usado para todo lo anterior |
| Afirmaciones que no se habían reproducido | Comprobadas con MongoDB 9.0.8 (réplica de un nodo) y Spring Boot 4.1.2: el mensaje sin conjunto de réplicas, `ChangeStreamHistoryLost` (286, se provoca desbordando un oplog de 990 MB), `ChangeStreamFatalError` (280, con una ficha cuya marca de tiempo está en el oplog pero no corresponde a ningún evento), el `IllegalStateException` de `block()` en un hilo `reactor-http-nio` (también con `Mono.just(x).map(...).block()`; lo único que no lanza la excepción es algo tan directo como `Mono.just(x).block()` o `Mono.fromCallable(...).block()`) y la desaparición del error de DNS en macOS (Apple Silicon, `osx-aarch_64`) con `netty-resolver-dns-native-macos` |
| Ejemplos de las sesiones 01 a 05 y 07, ejecutados de nuevo con los comandos de este README | Con otros puertos (`--server.port`, `--riesgo.url`, `--spring.datasource.url`, `--spring.r2dbc.url`) y las mismas órdenes (`mvn -q compile exec:java`, `exec:exec`, `exec:exec@con-blockhound`, `spring-boot:run`, `curl`, `xargs`, `bloqueo.sh`). La forma de cada salida y el orden de magnitud de cada medición coinciden con lo que muestran las sesiones |
| Código que aparece fuera de un bloque con `<!-- file: -->` | Fragmentos explicativos: el código completo y ejecutado está en los archivos |

Las mediciones se tomaron en un equipo de 18 núcleos con Docker compartido por otros entornos. Tus números serán otros; lo que debe repetirse es la **diferencia** entre "antes" y "después".
