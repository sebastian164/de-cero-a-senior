# Talleres de NoSQL: MongoDB y Redis

Una base de documentos y un almacén clave-valor en memoria, usados como se usan en un servicio Java de verdad: modelando, indexando, cuidando la consistencia, conectándolos a una arquitectura hexagonal y sabiendo dónde dejan de servir.

Esta ruta es la continuación práctica de la lección [NoSQL y caché del Nivel 8](../java/nivel-08-bases-de-datos/06-nosql-y-cache/). Allí están las familias NoSQL, los patrones de caché y `@Cacheable`; aquí no se repiten. Si no la has hecho, hazla primero.

**Punto de partida:** el banco de [`hexagonal/proyecto`](../hexagonal/proyecto/) para los ejemplos, y tu servicio de pedidos del módulo de [arquitectura hexagonal](../hexagonal/) para los talleres. Las sesiones 01 a 03 hablan con MongoDB directamente, para ver cada mecanismo aislado; la 04 conecta MongoDB al banco como adaptador de salida, y las 05 y 06 ponen Redis delante de sus puertos de entrada. El núcleo del banco no se modifica en ningún momento: se compila desde donde está.

## Orden

| # | Sesión | Qué terminas sabiendo hacer |
|---|---|---|
| 01 | [Modelar por consultas](01-modelar-por-consultas/) | Diseñar documentos a partir de las consultas y decidir qué se incrusta y qué se referencia |
| 02 | [Índices y explain](02-indices-y-explain/) | Leer un plan de ejecución y diseñar índices compuestos con la regla ESR |
| 03 | [Consistencia en MongoDB](03-consistencia-en-mongodb/) | Elegir entre operación atómica, actualización condicional, versión y transacción multidocumento |
| 04 | [MongoDB en el banco hexagonal](04-mongodb-en-el-banco-hexagonal/) | Escribir un adaptador de MongoDB que cumpla el mismo contrato que el de memoria |
| 05 | [Redis como caché](05-redis-como-cache/) | Poner cache-aside delante de un puerto, con TTL, invalidación y defensas contra estampida y penetración |
| 06 | [Redis más allá de la caché](06-redis-mas-alla-de-la-cache/) | Claves de idempotencia, límites de tasa y cerrojos con turno, sabiendo dónde dejan de servir |
| 07 | [Taller integrador](07-taller-integrador/) | El servicio de pedidos con MongoDB y Redis, con simulacro y defensa |

## Qué necesitas

- El [Nivel 8 de Java](../java/nivel-08-bases-de-datos/), sobre todo transacciones, concurrencia y NoSQL y caché.
- El módulo de [arquitectura hexagonal](../hexagonal/), al menos hasta la sesión 05 (concurrencia); para la 04 de esta ruta, también la [08](../hexagonal/08-pruebas-y-limites/) (el contrato del repositorio).
- JDK 21 y Maven 3.9 o superior.
- Docker, para el [entorno](entorno/). Si no lo has usado, haz antes los [talleres de Docker](../docker-kubernetes/docker/). No hace falta instalar `mongosh` ni `redis-cli`: vienen dentro de los contenedores.

## Cómo se ejecutan los ejemplos

Cada `ejemplo/antes` y cada `ejemplo/despues` es un proyecto Maven pequeño que hereda de [`pom.xml`](pom.xml), donde están fijadas las versiones de las bibliotecas y las opciones del compilador. Con el entorno levantado:

```bash
cd 01-modelar-por-consultas/ejemplo/antes
mvn -q compile exec:java
```

Desde esta carpeta, `mvn -q compile` compila los doce de una vez.

Cada ejemplo borra y crea su propia base de MongoDB (`nosql01_antes`, `nosql04_despues_contrato`…) y vacía el Redis del entorno al empezar. Por eso el entorno es solo para el curso: no apuntes estos ejemplos a nada que te importe.

## Cómo es cada sesión

El ciclo de la [metodología](../METODOLOGIA.md): idea con analogía, lo esencial, un ejemplo que funciona pero está mal, su análisis con la salida real, la versión corregida, errores comunes, taller con criterios de aceptación, cómo aparece en el trabajo real y preguntas senior.

Los ejemplos "antes" **funcionan**: con un usuario, nadie notaría nada. El problema aparece al ejecutarlos con volumen, con concurrencia o con un fallo a mitad, y cada README muestra esa ejecución.

## Dos dominios, a propósito

| | Dominio | Dónde |
|---|---|---|
| Ejemplos | El banco: cuentas, movimientos, transferencias, pagos | En cada sesión |
| Talleres | Tu servicio de pedidos: pedidos, líneas, clientes, catálogo | En cada sesión y en el integrador |

El ejemplo enseña la idea con un problema; el taller te pide aplicarla a otro. Así un taller no se resuelve copiando.

## Cómo entregar

Una rama por taller (`taller/nosql-01`, `taller/nosql-02`…), tu trabajo en `taller/solucion/` dentro de la sesión, un `ANALISIS.md` con lo que hiciste, lo que mediste y por qué, y un pull request contra `main`. No hay soluciones de referencia publicadas.

## Estado del código

| Qué | Cómo se comprobó al preparar el curso |
|---|---|
| Los doce ejemplos (sesiones 01 a 06, antes y después) | Compilados con Maven y `-Xlint:all -Werror`, y ejecutados contra MongoDB 9.0.8 (replica set de un nodo) y Redis 8.10.3 en Docker. Las salidas que muestran los README son de esas ejecuciones |
| Ejemplos sobre el banco hexagonal (sesiones 04 y 05) | Compilados junto con [`hexagonal/proyecto`](../hexagonal/proyecto/) sin modificarlo; el de la sesión 04 ejecuta el `ContratoDelRepositorio` de ese proyecto contra MongoDB real |
| Lo que varía entre ejecuciones | Los milisegundos, y los conteos de los ejemplos con hilos (actualizaciones perdidas, conflictos, reintentos). Lo que no varía es lo que cada sesión señala: que se pierden datos, que no se pierden, que el total cuadra |
| [Entorno](entorno/) | Levantado con `docker compose`; las órdenes de `mongosh` y `redis-cli` de su README se ejecutaron |
| Spring Data MongoDB y Spring Data Redis | Se nombran en "En el trabajo real", sin código en las sesiones. Sus afirmaciones se ejecutaron con Spring Boot 4.1.2 contra MongoDB y Redis reales: `@Version` lanza `OptimisticLockingFailureException`; `@Transactional` sin `MongoTransactionManager` no revierte nada y no avisa (Spring Boot no crea ninguno); la creación automática de índices viene apagada; `entryTtl` y el prefijo de claves del `RedisCacheManager` funcionan como se dice; el valor se serializa con la serialización de Java por defecto. Dos afirmaciones eran falsas y se corrigieron (sesión 05): `@Cacheable(sync = true)` no hace la carga única con el `RedisCacheManager` por defecto, y `@CacheEvict` dentro de `@Transactional` borra antes o después de confirmar según el orden de los proxies |
| Sesión 07 | No trae código: es el encargo del integrador |
| Los siete talleres | Resueltos y ejecutados contra MongoDB 9.0.8 (`mongosh` 2.14.2) y Redis 8.10.3 (JDK 21, controlador 5.14.0, Lettuce 7.9.0.RELEASE): las cifras que piden (sobreventa, estampida, desalojo, simulacro con dos instancias) salen como describen los enunciados. La solución del taller 04 parte del servicio del módulo hexagonal tal cual y pasa sus contratos contra memoria y MongoDB; la del 07 levanta MongoDB, Redis y dos instancias del servicio con una orden de Compose. Adaptaciones y huecos: el servicio del módulo hexagonal no tiene el estado `ENVIADO` ("pagar" es confirmar y no hay envío); no se probó el arranque desde un clon limpio con red ni la entrega en ramas con pull request. Las soluciones no se publican |

Versiones fijadas (las estables vigentes en noviembre de 2026):

| Pieza | Versión |
|---|---|
| Imagen de MongoDB | `mongo:9.0.8` |
| Imagen de Redis | `redis:8.10.3` |
| Controlador de MongoDB para Java (`org.mongodb:mongodb-driver-sync`) | 5.14.0 |
| Cliente de Redis (`io.lettuce:lettuce-core`) | 7.9.0.RELEASE |
| `org.slf4j:slf4j-simple` | 2.0.20 |
| `maven-compiler-plugin` / `exec-maven-plugin` / `build-helper-maven-plugin` | 3.16.0 / 3.7.0 / 3.8.0 |

Si cambias alguna, cámbiala en el [`pom.xml`](pom.xml) padre o en el [`compose.yaml`](entorno/compose.yaml), y vuelve a ejecutar los ejemplos: la salida debe seguir diciendo lo mismo.
