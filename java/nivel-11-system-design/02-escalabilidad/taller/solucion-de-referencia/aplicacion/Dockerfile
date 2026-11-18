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
