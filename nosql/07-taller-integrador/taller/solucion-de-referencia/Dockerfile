# La imagen del servicio de pedidos. Se construye después de ./preparar-imagen.sh, que deja en target/libs
# las clases de los talleres 04 a 07 y sus dependencias.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/libs /app/libs
ENTRYPOINT ["java", "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn", "-cp", "/app/libs/*", "integrador.Servidor"]
