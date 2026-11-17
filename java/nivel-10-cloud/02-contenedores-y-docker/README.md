# 02 — Contenedores y Docker

## Idea

Antes del contenedor de carga, cada mercancía se embarcaba distinto. El contenedor estandarizó la caja: lo que haya adentro, cualquier barco, tren o camión puede moverlo. Un contenedor de software hace lo mismo: empaqueta la aplicación con todo lo que necesita, y corre igual en tu portátil y en producción.

## Lo esencial

- **Imagen:** paquete inmutable con la aplicación y sus dependencias, construido por capas.
- **Contenedor:** una instancia en ejecución de una imagen. Es un proceso aislado que comparte el núcleo del sistema anfitrión; no es una máquina virtual.
- **Registro:** donde se almacenan y distribuyen las imágenes.
- **Dockerfile:** la receta para construir una imagen.

### Buenas prácticas para imágenes Java

| Práctica | Por qué |
|---|---|
| Construcción en varias etapas | La imagen final no lleva el JDK completo ni el código fuente |
| Imagen base pequeña y con versión fija | Menos superficie de ataque; construcciones reproducibles |
| Aprovechar la caché de capas | Copiar primero lo que cambia poco (dependencias) y después el código |
| Usuario sin privilegios | Un contenedor comprometido como `root` es más peligroso |
| Un proceso por contenedor | Facilita escalar, reiniciar y observar |
| Configuración por variables de entorno | La misma imagen sirve en todos los ambientes |
| Sin secretos en la imagen | Cualquiera con acceso a la imagen los leería |
| Análisis de vulnerabilidades | Las imágenes base envejecen |

### La JVM en contenedores

- La JVM moderna detecta los límites de memoria y CPU del contenedor.
- Prefiere `-XX:MaxRAMPercentage=75` a un `-Xmx` fijo.
- Reserva memoria para lo que está fuera del montón: metaspace, hilos, búferes.
- Si el contenedor supera su límite, el sistema lo mata sin excepción ni aviso (*OOMKilled*).

### Docker Compose

Describe varios contenedores y cómo se conectan. Es la herramienta para el entorno local: la aplicación, la base, el broker y lo que haga falta, con un solo comando.

## Ejemplo

```dockerfile
# Etapa de construcción
FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline          # esta capa se reutiliza mientras no cambie el pom
COPY src src
RUN ./mvnw package -DskipTests

# Etapa final
FROM eclipse-temurin:21-jre
RUN useradd --system aplicacion
USER aplicacion
WORKDIR /app
COPY --from=construccion /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```

Alternativas sin Dockerfile: `./mvnw spring-boot:build-image` (Buildpacks) o Jib.

```yaml
services:
  app:
    build: .
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/tienda
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: local
    depends_on:
      db:
        condition: service_healthy
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: tienda
      POSTGRES_PASSWORD: local
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "postgres"]
```

## Errores comunes

- Etiqueta `latest`: no se sabe qué versión está desplegada.
- Imágenes de un gigabyte con herramientas de construcción adentro.
- Ejecutar como `root`.
- Secretos en el Dockerfile o en capas intermedias.
- Guardar datos dentro del contenedor (se pierden al recrearlo); para eso existen los volúmenes.
- Varios procesos en un contenedor.
- Construir una imagen distinta por ambiente.
- Ignorar las señales de terminación: el contenedor muere a la fuerza.

## Taller

1. Escribe un Dockerfile de una sola etapa para una aplicación Spring Boot y anota el tamaño de la imagen.
2. Conviértelo a varias etapas, con usuario sin privilegios y capas bien ordenadas. Compara tamaño y tiempo de reconstrucción tras cambiar una línea de código.
3. Monta con Docker Compose la aplicación, PostgreSQL y un broker, con comprobaciones de salud.
4. Limita la memoria del contenedor por debajo de lo que la aplicación necesita y observa qué ocurre. Ajusta la JVM.
5. Analiza la imagen con una herramienta de vulnerabilidades (por ejemplo, Trivy) y corrige un hallazgo.
6. Demuestra que la misma imagen funciona con dos configuraciones distintas pasadas por entorno.

Rama `taller/n10-02-docker`.

Para practicar a fondo, paso a paso y con criterios de aceptación, están los [seis talleres de Docker](../../../docker-kubernetes/docker/).

## Preguntas senior

1. Diferencia entre contenedor y máquina virtual.
2. ¿Por qué construir en varias etapas?
3. ¿Qué pasa cuando un contenedor de Java supera su límite de memoria y cómo lo previenes?
4. ¿Por qué la misma imagen debe servir para todos los ambientes?
5. ¿Cómo manejas los secretos con contenedores?
6. ¿Qué es la caché de capas y cómo se aprovecha?
