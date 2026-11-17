# Imagen en capas: las dependencias (que casi no cambian) quedan en una capa que se reutiliza entre construcciones.
FROM eclipse-temurin:21-jre-alpine AS extraer
WORKDIR /extraer
COPY target/tienda.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination capas

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=extraer /extraer/capas/dependencies/ ./
COPY --from=extraer /extraer/capas/spring-boot-loader/ ./
COPY --from=extraer /extraer/capas/snapshot-dependencies/ ./
COPY --from=extraer /extraer/capas/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
