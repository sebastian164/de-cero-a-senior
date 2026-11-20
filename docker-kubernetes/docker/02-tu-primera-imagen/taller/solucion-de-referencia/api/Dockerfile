# Bitácora API, versión sencilla: una sola etapa con el JDK.
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY Servidor.java .
# Se compila al construir, no al arrancar.
RUN javac -d clases Servidor.java
EXPOSE 8080
# Forma exec: java es el PID 1 y recibe SIGTERM.
ENTRYPOINT ["java", "-cp", "clases", "Servidor"]
