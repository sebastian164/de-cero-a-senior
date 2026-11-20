# Bitácora API 1.0.0: dos etapas, entorno de ejecución reducido con jlink sobre Alpine.
# Etapa 1: compilar y fabricar un entorno de ejecución con solo los módulos que usa Servidor.java.
FROM eclipse-temurin:21-jdk-alpine AS construccion
WORKDIR /src
COPY Servidor.java .
RUN javac -d /clases Servidor.java \
 && jlink --add-modules java.base,jdk.httpserver \
          --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
          --output /jre

# Etapa 2: Alpine, el entorno de ejecución y las clases. Sin javac, sin código fuente.
FROM alpine:3.22
RUN addgroup -S -g 10001 bitacora \
 && adduser -S -D -H -u 10001 -G bitacora bitacora \
 && mkdir /datos && chown 10001:10001 /datos
COPY --from=construccion /jre /opt/jre
COPY --from=construccion /clases /app
COPY --chmod=755 entrada.sh /entrada.sh
ENV PATH=/opt/jre/bin:$PATH \
    DIRECTORIO_DATOS=/datos
USER 10001:10001
EXPOSE 8080
# wget viene en Alpine (busybox). 127.0.0.1 y no localhost: localhost puede resolverse a ::1.
# start-period cubre el arranque lento: los fallos dentro de ese margen no cuentan.
HEALTHCHECK --start-period=30s --interval=5s --timeout=3s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/salud/listo || exit 1
# La JVM 21 ya lee el límite del cgroup; estas opciones la ajustan a contenedores pequeños.
ENTRYPOINT ["/entrada.sh"]
