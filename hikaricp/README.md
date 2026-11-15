# Talleres de HikariCP: el pool de conexiones

HikariCP es el pool de conexiones a base de datos que Spring Boot usa por defecto. Casi todo servicio Java lo tiene dentro, casi nadie lo mira, y es protagonista de uno de los incidentes más comunes en producción: `Connection is not available`.

Esta ruta es para la [ruta Java](../java/), después del [Nivel 8 (bases de datos)](../java/nivel-08-bases-de-datos/).

**Punto de partida:** el servicio de pedidos que construyes en el módulo de [arquitectura hexagonal](../hexagonal/). Cada taller indica dónde va su trabajo en ese servicio, y la sesión 06 muestra, con código que se ejecuta sobre el banco de ese módulo, por qué la forma de un caso de uso decide el tamaño del pool.

## Orden

| # | Sesión | Qué terminas sabiendo hacer |
|---|---|---|
| 01 | [Qué es un pool de conexiones](01-que-es-un-pool/) | Explicar por qué existe y usar HikariCP sin Spring |
| 02 | [La configuración que importa](02-configuracion-que-importa/) | Dimensionar un pool con una cuenta y con mediciones |
| 03 | [Fugas de conexiones](03-fugas-de-conexiones/) | Encontrar y eliminar una fuga con las herramientas del pool |
| 04 | [Agotamiento del pool](04-agotamiento-del-pool/) | Reconocer y corregir conexiones retenidas de más |
| 05 | [Operación en producción y taller integrador](05-operacion-y-taller-integrador/) | Vigilar el pool y diagnosticarlo en un simulacro |
| 06 | [El pool en un servicio hexagonal](06-el-pool-en-un-servicio-hexagonal/) | Ubicar el pool en la arquitectura y arreglar un agotamiento con una decisión de diseño |

## Qué necesitas

- JDK 21. Los ejemplos en Java puro se ejecutan con `java Main.java`, sin dependencias.
- Para los talleres: un proyecto con HikariCP y el controlador de PostgreSQL; desde la sesión 02, Spring Boot con Actuator.
- Docker, para el [entorno](entorno/).
- Una herramienta de carga.

## Cómo es cada sesión

El ciclo de la [metodología](../METODOLOGIA.md): idea con analogía, lo esencial, un ejemplo que funciona pero está mal, su análisis, la versión corregida, errores comunes, taller con criterios de aceptación, cómo aparece en el trabajo real y preguntas senior.

Los ejemplos de las sesiones 01, 03 y 04 son **modelos** en Java puro: un pool hecho con una cola o un semáforo, sin base de datos. Sirven para ver el mecanismo aislado. Los talleres se hacen con HikariCP y PostgreSQL de verdad.

## Cómo entregar

Una rama por taller (`taller/hikari-01`…), tu trabajo en `taller/solucion/` dentro de la sesión, un `ANALISIS.md` con lo que mediste y un pull request contra `main`. No hay soluciones de referencia publicadas.

## Estado del código

| Qué | Cómo se comprobó al preparar el curso |
|---|---|
| Ejemplos en Java puro (sesiones 01, 03 y 04): seis programas | Compilados con `-Xlint:all -Werror` y ejecutados con JDK 21. El de la sesión 04 usa hilos y tiempos reales: en una máquina muy cargada, sus conteos pueden variar |
| Ejemplo sobre el banco hexagonal (sesión 06): un proyecto de varios archivos, con un adaptador JDBC real | Compilado con `-Xlint:all -Werror` junto con el núcleo de [`hexagonal/proyecto`](../hexagonal/proyecto/) y ejecutado. El adaptador JDBC se ejercitó con conexiones falsas, **no contra una base de datos**. Usa hilos y tiempos reales: sus conteos pueden variar |
| Compose del [entorno](entorno/) | Validado con `docker compose config`; no se levantó |
| Código con HikariCP, archivos de configuración de Spring y consultas SQL | Ilustrativos: **no se ejecutaron** |

Versión vigente al escribir: HikariCP 7.1 (Java 11 o superior). Los valores por defecto que cita la sesión 02 son los de la serie 7; alguno cambió respecto a versiones anteriores.
