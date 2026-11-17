# Una sola imagen para los siete servicios: el artefacto ejecutable de este módulo (mvn package) y un JRE 21.
FROM eclipse-temurin:21-jre
COPY target/sagas-07-1.0.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
