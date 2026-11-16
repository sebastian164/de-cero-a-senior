# Taller 07-07 — Observabilidad y producción (solución)

Spring Boot 4.1.1, Micrometer (registro Prometheus), `spring-boot-starter-opentelemetry` (trazas por OTLP), Prometheus v3.5.0, Grafana/Tempo de `grafana/otel-lgtm:0.40.0`. Dos servicios son el mismo jar (`servicio-a` en 42201, `servicio-b` en 42202): A confirma el pedido y llama a B (`/b/cobro/{id}`), que cobra.

`mvn test` (4 pruebas, app real en 42210) y `./escenario.sh` (Docker + dos procesos) cubren los cinco puntos.

1. **Actuator con sondas y apagado ordenado.** `management.endpoint.health.probes.enabled=true`; `server.shutdown=graceful` y `spring.lifecycle.timeout-per-shutdown-phase=15s`. Pruebas: `/actuator/health/liveness` y `/readiness` responden 200; con una dependencia externa simulada caída, `/actuator/health` responde 503 pero **liveness y readiness siguen en 200** (la sonda de vida no depende de servicios externos). Apagado ordenado: se lanza `/lento?ms=2500`, a los 600 ms se cierra el contexto; la petición termina con 200 `terminada`, el cierre tardó más de 1 s esperándola y después el puerto rechaza conexiones.
2. **Métricas.** Negocio: `pedidos.confirmados` (contador), `pedidos.rechazados{motivo}` (contador, etiqueta de cardinalidad baja). Técnicas: `pagos.cobro.duracion` (temporizador con histograma) y `pedidos.en.proceso` (medidor de saturación). Prometheus las raspa cada 5 s; Grafana (`grafana-dashboard.json`, 4 paneles: tasa de confirmados, p95 del cobro, p95 HTTP por ruta, pedidos en proceso) se aprovisiona por API y se consulta a través de su origen de datos (devuelve `pedidos_confirmados_total`).
3. **Registro JSON con identificador de traza.** `logging.structured.format.console=ecs`: cada línea es JSON con `traceId`, `spanId` y `service.name`. Enviando `traceparent` con un `traceId` conocido, el mismo `traceId` aparece en los registros de `servicio-a` ("Pedido confirmado") y de `servicio-b` ("Cobrando"): se sigue una petición a través de dos servicios. En Boot 4.1 el campo se llama `traceId` (no `trace.id`).
4. **Tres alertas** (`infra/alertas.yml`, validadas con `promtool check rules`: 3 reglas): 
   | Alerta | Umbral | Acción de quien la recibe |
   |---|---|---|
   | `LatenciaDeCobroAlta` | p95 del cobro > 300 ms durante 5 min (en la prueba, 15 s) | Comparar `http_server_requests` de `/b/cobro` con `pagos.cobro.duracion`; si coinciden, el retraso está dentro de la pasarela: revisar su estado y su tiempo límite; escalar a pagos si pasan 15 min |
   | `TasaDeErroresAlta` | más del 5 % de respuestas 5xx durante 2 min | Buscar por `traceId` las peticiones fallidas, identificar el servicio, revertir el último despliegue si coincide |
   | `InstanciaCaida` | `up == 0` durante 1 min | Comprobar liveness y registros; el orquestador reinicia si la sonda de vida falla; si se repite, escalar |
   La primera pasó a `firing` en Prometheus durante el experimento del punto 5.
5. **Latencia artificial hallada solo con métricas y trazas.** Se reinicia B con `app.latencia-cobro-ms=450` (el diagnóstico no mira el código). Resultado real del experimento (`target/escenario/diag.txt`):
   - Métricas: p95 normal del cobro 0,007 s; con la latencia, p95 HTTP de `/a/pedido/{id}` y de `/b/cobro/{id}` ≈ 0,53 s (misma cifra: A espera a B) y p95 de `pagos.cobro.duracion` ≈ 0,53 s (el tiempo está dentro del cobro, no en la red de A a B).
   - Trazas (Tempo, TraceQL `{ duration > 300ms }`): la traza lenta tiene cuatro spans: `servicio-a http get /a/pedido/{id}` 466 ms → `http get` (cliente) 464 ms → `servicio-b http get /b/cobro/{id}` 463 ms → `servicio-b pagos.cobro` 461 ms. El tiempo se gasta íntegro en `pagos.cobro` de `servicio-b`. El script lo comprueba con `assert`.

## Hallazgos para el curso (README 07-07)

- Con `publishPercentileHistogram()` el registro Prometheus publica solo buckets; los percentiles del lado cliente (`quantile="0.95"`) no salen. Para p95/p99 con varias instancias se usa histograma y `histogram_quantile` en Prometheus (el README solo menciona `publishPercentiles`; ver proyecto de verificación para ese caso).
- En Spring Boot 4 el cliente HTTP instrumentado (`RestClient.Builder`) exige `spring-boot-starter-restclient`; el seguimiento por trazas exige `spring-boot-starter-opentelemetry`, que también activa el envío de **métricas** por OTLP a `localhost:4318` si no se desactiva (`management.otlp.metrics.export.enabled=false`).
- La interfaz `HealthIndicator` está en `org.springframework.boot.health.contributor` (Boot 4).
