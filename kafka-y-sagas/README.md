# Talleres de Kafka y sagas

Ruta práctica sobre mensajería y consistencia entre servicios. Va después del [Nivel 9 de Java](../java/nivel-09-sistemas-distribuidos/), cuyas sesiones 03 y 04 dan el panorama; aquí se construye.

Son dos partes en orden: primero **Kafka**, la herramienta para mover hechos entre servicios sin perderlos; después **sagas**, el patrón para que una operación de varios servicios termine bien o se deshaga bien.

## Orden

### [Kafka](kafka/)

| # | Sesión | Qué terminas sabiendo hacer |
|---|---|---|
| 01 | [El registro de eventos](kafka/01-el-registro-de-eventos/) | Temas, particiones, posiciones, claves y retención |
| 02 | [Productores](kafka/02-productores/) | Escribir sin perder ni duplicar; réplicas y confirmaciones |
| 03 | [Consumidores y grupos](kafka/03-consumidores-y-grupos/) | Grupos, confirmación de posición y retraso |
| 04 | [Garantías de entrega e idempotencia](kafka/04-entrega-e-idempotencia/) | Consumidores correctos con repeticiones y mensajes defectuosos |
| 05 | [Eventos como contrato](kafka/05-eventos-como-contrato/) | Diseñar eventos y evolucionarlos sin romper |
| 06 | [Kafka con Spring Boot](kafka/06-kafka-con-spring-boot/) | Listeners, política de errores y pruebas de integración |
| 07 | [Outbox transaccional](kafka/07-outbox-transaccional/) | Guardar y publicar sin doble escritura |

### [Sagas](sagas/)

| # | Sesión | Qué terminas sabiendo hacer |
|---|---|---|
| 01 | [Transacciones entre servicios](sagas/01-transacciones-entre-servicios/) | Diseñar pasos y compensaciones |
| 02 | [Saga orquestada](sagas/02-saga-orquestada/) | Un orquestador como máquina de estados duradera |
| 03 | [Saga coreografiada](sagas/03-saga-coreografiada/) | Coordinar por eventos y saber cuándo no |
| 04 | [Cuando todo falla](sagas/04-cuando-todo-falla/) | Repeticiones, desorden, tiempos límite y aislamiento |
| 05 | [Sagas con Kafka y Spring Boot](sagas/05-sagas-con-kafka-y-spring-boot/) | Llevarlo a infraestructura real |
| 06 | [Taller integrador](sagas/06-taller-integrador/) | La tienda completa, con simulacro y defensa |

## Qué necesitas

- Haber hecho los [talleres de Docker](../docker-kubernetes/docker/): el entorno corre en contenedores.
- JDK 21. Los ejemplos en Java puro se ejecutan con `java Main.java`, sin Maven.
- Para los talleres con código real: Maven o Gradle, el cliente de Kafka y, desde la sesión 06, Spring Boot.
- Docker, para [Kafka](entorno/) y PostgreSQL.

Si vienes de una ruta de frontend, las ideas (registro de eventos, idempotencia, compensación) valen igual; los talleres con código están pensados para Java, pero los de las sesiones 01 de Kafka y 01 a 04 de sagas no dependen de Spring.

## Dos dominios, a propósito

| | Dominio | Dónde |
|---|---|---|
| Ejemplos | Movimientos de cuenta, pagos y reservas de viaje (vuelo, hotel, cobro) | En cada sesión |
| Talleres | Una tienda: Pedidos, Pagos, Inventario y Envíos | Desde Kafka 05 hasta el final |

El ejemplo enseña la idea con un problema; el taller te pide aplicarla a otro. Así un taller no se resuelve copiando.

## Cómo es cada sesión

El ciclo de la [metodología](../METODOLOGIA.md): idea con analogía, lo esencial, un ejemplo que funciona pero está mal, su análisis, la versión corregida, errores comunes, taller con criterios de aceptación, cómo aparece en el trabajo real y preguntas senior.

## Cómo entregar

Una rama por taller (`taller/kafka-03`, `taller/sagas-02`…), tu trabajo en `taller/solucion/` dentro de la sesión, un `ANALISIS.md` con lo que hiciste, lo que mediste y por qué, y un pull request contra `main`. No hay soluciones de referencia publicadas.

## Estado del código

| Qué | Cómo se comprobó al preparar el curso |
|---|---|
| Ejemplos en Java puro (Kafka 03, 04 y 07; sagas 01 a 04): catorce programas | Compilados con `-Xlint:all -Werror` y ejecutados con JDK 21; la salida de cada uno es la que describe su sesión |
| Archivos de Compose del [entorno](entorno/) | Validados con `docker compose config` |
| Código con el cliente de Kafka y con Spring | Ilustrativo: **no se compiló**, porque requiere descargar bibliotecas que no estaban disponibles |
| Órdenes de consola de Kafka y el propio broker | **No se ejecutaron**: no había motor de Docker |

Versiones vigentes al escribir: Apache Kafka 4.3 (sin ZooKeeper desde la 4.0) y Spring for Apache Kafka 4. Si una orden o una clase no coincide con tu versión, manda la documentación oficial.
