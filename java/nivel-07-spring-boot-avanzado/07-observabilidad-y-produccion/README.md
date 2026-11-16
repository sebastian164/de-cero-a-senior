# 07 — Observabilidad y preparación para producción

## Idea

Un carro tiene tablero: velocidad, temperatura, combustible, luces de aviso. Sin tablero se puede conducir, hasta que algo falla y no hay forma de saber qué. Un servicio en producción sin observabilidad es un carro sin tablero.

## Lo esencial

### Los tres pilares

| Pilar | Responde | Herramientas |
|---|---|---|
| **Registros (*logs*)** | ¿Qué pasó en este punto? | SLF4J + Logback, formato JSON, agregador (ELK, Loki) |
| **Métricas** | ¿Cuánto, con qué frecuencia, qué tan rápido? | Micrometer → Prometheus → Grafana |
| **Trazas** | ¿Por dónde pasó esta petición y dónde tardó? | Micrometer Tracing / OpenTelemetry → Tempo, Jaeger |

Lo que los une es el **identificador de correlación** (traza): debe aparecer en cada línea de registro y propagarse entre servicios.

### Actuator

`spring-boot-starter-actuator` expone puntos de operación:

| Punto | Para qué |
|---|---|
| `/actuator/health` | Estado; con grupos `liveness` y `readiness` para Kubernetes |
| `/actuator/metrics`, `/actuator/prometheus` | Métricas |
| `/actuator/info` | Versión desplegada |
| `/actuator/loggers` | Cambiar niveles de registro en caliente |

Expón solo lo necesario y protégelo.

### Qué medir

Las cuatro señales doradas: **latencia** (percentiles p95 y p99, no promedios), **tráfico**, **errores** y **saturación** (pool de conexiones, hilos, memoria). Además, métricas de negocio: pedidos confirmados por minuto dice más que el uso de CPU.

### Buenas prácticas de registro

- Niveles con criterio: `ERROR` requiere acción; `WARN` algo anómalo; `INFO` hitos de negocio; `DEBUG` detalle.
- Registro estructurado (JSON) con campos, no texto para leer con expresiones regulares.
- Nunca datos personales, contraseñas ni tokens.
- Mensajes parametrizados: `log.info("Pedido {} confirmado", id)`.

### Preparación para producción

- **Apagado ordenado:** `server.shutdown=graceful`, para terminar las peticiones en curso.
- **Tiempos límite** en toda llamada saliente y en el pool de conexiones.
- **Sondas** de vida y de disponibilidad bien definidas: la de vida no debe depender de servicios externos.
- **Configuración por entorno** y secretos externos.
- **Migraciones de base** automatizadas (Nivel 8).

## Ejemplo

```java
@Service
class ConfirmarPedido {

    private final Counter confirmados;
    private final Timer tiempoDeCobro;

    ConfirmarPedido(MeterRegistry registro) {
        this.confirmados = Counter.builder("pedidos.confirmados")
                .description("Pedidos confirmados")
                .register(registro);
        this.tiempoDeCobro = Timer.builder("pagos.cobro.duracion")
                .publishPercentiles(0.95, 0.99)
                .register(registro);
    }

    void ejecutar(PedidoId id) {
        tiempoDeCobro.record(() -> pasarela.cobrar(id));
        confirmados.increment();
        log.info("Pedido confirmado pedidoId={}", id.valor());
    }
}
```

```yaml
management:
  endpoints.web.exposure.include: health,info,prometheus
  endpoint.health.probes.enabled: true
server:
  shutdown: graceful
```

Verificado con Spring Boot 4.1.1 (Micrometer, Prometheus v3.5.0): hacen falta `spring-boot-starter-actuator` y `micrometer-registry-prometheus`; con solo `publishPercentiles(0.95, 0.99)`, `/actuator/prometheus` publica `pagos_cobro_duracion_seconds{quantile="0.95"}` y `{quantile="0.99"}` por instancia. Esos cuantiles no se pueden sumar entre réplicas: si además se llama `publishPercentileHistogram()`, el registro publica solo los *buckets* y el p95 se calcula en Prometheus con `histogram_quantile(0.95, sum by (le) (rate(pagos_cobro_duracion_seconds_bucket[5m])))`. Las trazas necesitan `spring-boot-starter-opentelemetry`; ese *starter* también activa por defecto el envío de métricas por OTLP (`management.otlp.metrics.export.enabled=false` si no se quiere), y el registro JSON (`logging.structured.format.console=ecs`) incluye `traceId` y `spanId`.

## Errores comunes

- Registrar todo en `INFO` o nada en absoluto.
- Capturar una excepción, registrarla y relanzarla en cada capa.
- Promedios de latencia que ocultan a los usuarios lentos.
- Métricas con etiquetas de cardinalidad alta (un identificador de usuario por etiqueta).
- Alertas por todo: nadie las atiende.
- Sonda de vida que consulta la base de datos: una caída de la base reinicia todas las réplicas.
- No propagar la traza a tareas asíncronas ni a mensajes.

## Taller

1. Configura Actuator con sondas y verifica el apagado ordenado con una petición lenta en curso.
2. Agrega dos métricas de negocio y dos técnicas, y visualízalas en Prometheus y Grafana (Docker Compose).
3. Configura registro en JSON con identificador de traza y sigue una petición a través de dos servicios.
4. Define tres alertas con su umbral y la acción que debe tomar quien la reciba.
5. Provoca una latencia artificial y encuéntrala usando solo métricas y trazas, sin leer el código.

Rama `taller/n7-07-observabilidad`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. Diferencia entre monitoreo y observabilidad.
2. ¿Por qué percentiles y no promedios?
3. Sonda de vida frente a sonda de disponibilidad: ¿qué comprueba cada una?
4. ¿Cómo sigues una petición que atraviesa cinco servicios y una cola?
5. ¿Qué es un SLO y cómo lo defines con el negocio?
6. Un servicio responde lento solo a ratos. ¿Cómo lo investigas?
