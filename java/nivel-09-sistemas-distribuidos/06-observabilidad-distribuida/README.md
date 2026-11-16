# 06 — Observabilidad distribuida y operación

Requisito: [Observabilidad en Spring Boot](../../nivel-07-spring-boot-avanzado/07-observabilidad-y-produccion/).

## Idea

Un paquete pasa por cinco bodegas antes de llegar. Si se retrasa, sin un número de guía hay que llamar a las cinco. Con la guía, se ve por dónde pasó, cuánto estuvo en cada una y dónde se detuvo. En un sistema distribuido, esa guía es el identificador de traza.

## Lo esencial

### Trazas distribuidas

- **Traza:** el recorrido completo de una petición. **Tramo (*span*):** cada operación dentro de ella, con su duración y su relación de padre a hijo.
- **Propagación de contexto:** el identificador de traza viaja en las cabeceras (estándar W3C `traceparent`) de HTTP y de los mensajes. Si un servicio no lo propaga, la traza se corta.
- **OpenTelemetry** es el estándar para generar y exportar trazas, métricas y registros, independiente del proveedor.
- **Muestreo:** no se guarda todo. Lo habitual es conservar un porcentaje, más todas las que tuvieron error o fueron lentas.

### Correlación

El identificador de traza debe aparecer en cada línea de registro. Así, desde una alerta se llega a la traza, y desde la traza a los registros exactos de cada servicio.

### Objetivos de nivel de servicio

| Término | Qué es | Ejemplo |
|---|---|---|
| **SLI** | Lo que se mide | Porcentaje de peticiones correctas en menos de 300 ms |
| **SLO** | El objetivo interno | 99,9 % en 30 días |
| **SLA** | El compromiso contractual, con consecuencias | 99,5 % o hay descuento |
| **Presupuesto de error** | Cuánto se puede fallar sin incumplir el SLO | 0,1 % ≈ 43 minutos al mes |

El presupuesto de error convierte la discusión "estabilidad contra velocidad" en un número: si queda presupuesto, se despliega; si se agotó, se prioriza la fiabilidad.

### Alertas

- Se alerta por **síntomas que afectan al usuario** (errores, latencia), no por causas (CPU alta).
- Cada alerta debe ser accionable y tener un procedimiento (*runbook*).
- Demasiadas alertas equivalen a ninguna.

### Incidentes

1. **Mitigar primero:** revertir el despliegue, desviar el tráfico, apagar la función. Diagnosticar después.
2. Un responsable del incidente y un canal de comunicación.
3. **Análisis posterior sin culpables:** qué pasó, por qué el sistema lo permitió, qué se cambia. El objetivo es mejorar el sistema, no señalar a una persona.

### Despliegue seguro

| Estrategia | Cómo |
|---|---|
| Progresivo (*rolling*) | Se reemplazan las instancias por tandas |
| Azul-verde | Dos entornos completos; se cambia el tráfico de golpe |
| Canario | La versión nueva recibe primero un pequeño porcentaje |
| Banderas de función | El código se despliega apagado y se activa aparte |

Separar **desplegar** de **liberar** reduce el riesgo.

## Ejemplo

```yaml
management:
  tracing.sampling.probability: 0.1
  opentelemetry.tracing.export.otlp.endpoint: http://otel-collector:4318/v1/traces
logging:
  pattern.level: "%5p [${spring.application.name},%X{traceId},%X{spanId}]"
spring.kafka:
  template.observation-enabled: true
  listener.observation-enabled: true
```

En Spring Boot 4 hace falta el *starter* `spring-boot-starter-opentelemetry`, y la clave de exportación es `management.opentelemetry.tracing.export.otlp.endpoint`: la antigua `management.otlp.tracing.endpoint` ya no existe y, con ella, la aplicación sigue muestreando pero no exporta ninguna traza (verificado con Spring Boot 4.1.1 y Grafana Tempo). Con Micrometer Tracing, las llamadas con `RestClient` o `WebClient` construidos a partir de los *builders* de Spring propagan el contexto automáticamente. En Kafka no es automático: hay que activar la observación del `KafkaTemplate` y de los listeners (las dos últimas claves del YAML); sin ellas la traza HTTP sigue entera, pero el envío y la recepción del mensaje no dejan ningún tramo (comprobado con Spring Boot 4.1.1 y Tempo). Lo que se rompe con facilidad: hilos creados a mano y `@Async` sin un ejecutor que propague el contexto.

## Errores comunes

- Trazas que se cortan en el primer salto asíncrono.
- Registros sin identificador de traza.
- Alertar por causas y no por síntomas.
- SLO del 100 %: imposible y además impide cambiar.
- Análisis posteriores que terminan en "error humano".
- Diagnosticar durante un incidente antes de mitigarlo.
- Tableros que nadie mira y que no responden ninguna pregunta.
- Muestreo que descarta justo las trazas con error.

## Taller

1. Con Docker Compose, levanta dos servicios, Kafka y una pila de observabilidad (OpenTelemetry Collector, Tempo o Jaeger, Prometheus, Grafana).
2. Sigue una petición a través de una llamada HTTP y un mensaje de Kafka en una sola traza.
3. Rompe la propagación con un `@Async` y arréglala.
4. Define un SLI y un SLO para un servicio y calcula su presupuesto de error mensual.
5. Escribe dos alertas basadas en síntomas con su procedimiento.
6. Simula un incidente (introduce latencia en un servicio) y redacta el análisis posterior sin culpables.

Rama `taller/n9-06-observabilidad`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Cómo sigues una petición que atraviesa servicios síncronos y asíncronos?
2. SLI, SLO y SLA: diferencias y relación.
3. ¿Para qué sirve el presupuesto de error?
4. ¿Qué hace que una alerta sea buena?
5. Durante un incidente, ¿qué haces primero?
6. Despliegue canario frente a azul-verde.
