# Taller 10-02: contenedores y Docker (solución en local)

La aplicación es el servicio `pedidos` (Spring Boot 4, Java 21) de la carpeta `aplicacion/`. Todo se ejecuta con `taller.sh` en Docker Desktop (motor 29.9), con las imágenes `eclipse-temurin:21-jdk` y `eclipse-temurin:21-jre`, `postgres:16`, `apache/kafka:4.3.1` y Trivy 0.76.2. Las cifras son de una máquina de 12 núcleos y 16 GB; las tuyas cambiarán, las relaciones no.

## 1. Una sola etapa (`Dockerfile.una-etapa`)

Copia todo el proyecto, compila con Maven dentro de la imagen y arranca el JAR. La imagen lleva el JDK, Maven, el código fuente y la caché de dependencias, y corre como `root`. Tamaño: **1237 MB**.

## 2. Varias etapas, usuario sin privilegios y capas ordenadas (`aplicacion/Dockerfile`)

Es el `Dockerfile` del README. Las dependencias se descargan en una capa propia (`COPY mvnw pom.xml` y `RUN ./mvnw dependency:go-offline`) que solo se invalida si cambia el `pom.xml`; el código va después. La etapa final parte del JRE, crea el usuario `aplicacion` y copia solo el JAR.

| Medida | Una etapa | Varias etapas |
|---|---|---|
| Tamaño | 1237 MB | **667 MB** |
| Reconstrucción tras cambiar una línea de código | 20 s | **5 s** |
| `javac` y código fuente en la imagen | sí | no |
| Usuario | `root` | `aplicacion` |

El script comprueba las cuatro diferencias. La ganancia en tiempo viene de la caché de capas: con el código modificado, la capa de dependencias se reutiliza.

## 3. Compose: aplicación, PostgreSQL y Kafka con comprobaciones de salud (`compose.yaml`)

Cada servicio declara su `healthcheck` (`/actuator/health/readiness` la aplicación, `pg_isready` la base, `kafka-broker-api-versions.sh` el broker) y la aplicación espera con `depends_on: condition: service_healthy`. El script espera a que los tres estén `healthy`, crea un pedido por HTTP, comprueba que está en PostgreSQL y que el mensaje llegó al tópico `pedidos-creados` leyéndolo con el consumidor de consola.

Un error real que apareció al ejecutarlo: sin `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` la aplicación no arranca (`The server requested SCRAM-based authentication, but no password was provided`). Por eso el Compose los pasa por el entorno, y la clave viene de la variable `CLAVE_BD`, no está escrita en el archivo.

## 4. Memoria del contenedor por debajo de lo que la aplicación necesita

| Configuración (contenedor de 300 MB) | Resultado |
|---|---|
| `-Xmx1g` fijo | El montón puede crecer más que el contenedor: al pedir 600 MB el núcleo mata el proceso. `OOMKilled=true`, código de salida **137**, sin ninguna excepción de Java en el registro |
| `-XX:MaxRAMPercentage=50` (sin `-Xmx`) | La JVM calcula un montón de unos **145 MB**. La misma petición falla con `OutOfMemoryError: Java heap space`, el servicio responde 500 y **el contenedor sigue vivo** |

Lección: una excepción de Java se puede capturar, registrar y diagnosticar; un `OOMKilled` no deja rastro en la aplicación. Con 300 MB el 75 % del README deja unos 75 MB para metaspace, hilos y código compilado, que no alcanzan; se usó el 50 %. Con límites mayores, el 75 % funciona.

## 5. Vulnerabilidades con Trivy

Trivy 0.76.2 sobre la imagen sin el parche del `pom.xml` (archivo `pom-parche-trivy.xml`) encuentra **3 hallazgos HIGH o CRITICAL**, todos en dependencias transitivas de `kafka-clients`: `snappy-java` y `lz4-java`. La corrección es fijar versiones corregidas en `dependencyManagement` (`snappy-java` 1.1.10.9 y `lz4-java` 1.11.4). Con el parche (el `pom.xml` de `aplicacion/`) la imagen queda con **0 hallazgos**.

## 6. La misma imagen con dos configuraciones

Dos contenedores de la misma imagen (mismo ID), uno con `APP_AMBIENTE=pruebas` y la base `pruebas`, otro con `APP_AMBIENTE=produccion` y la base `produccion`. Responden con su configuración y un pedido creado en uno no aparece en el otro. La imagen no cambia entre ambientes; lo único que cambia es el entorno.

## Cómo reproducirlo

Ver `README.md`. Si algún valor medido te sale muy distinto (por ejemplo, la reconstrucción de una etapa no es más lenta que la de varias), revisa que no tengas otra construcción en curso y que Docker no esté reutilizando una caché distinta.
