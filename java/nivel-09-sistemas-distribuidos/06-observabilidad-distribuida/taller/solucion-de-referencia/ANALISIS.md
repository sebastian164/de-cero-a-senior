# Taller n9-06: observabilidad distribuida (solución)

`ObservabilidadTest` (6 pruebas) con dos servicios Spring Boot 4.1.1 (A en 43061, B en 43062), Kafka 4.3.1 (43092) y la imagen `grafana/otel-lgtm:0.40.0` (Collector OTLP, Tempo, Prometheus, Loki, Grafana; 43318 OTLP HTTP, 43200 Tempo, 43090 Prometheus). Corrida del 2026-11-15.

## 1 y 2. Pila y una sola traza

`compose.yml` levanta Kafka y la pila. A recibe `GET /pedidos/{id}`, llama a B por HTTP (`RestClient` creado del *builder* de Spring) y publica un mensaje en Kafka que B consume. La prueba consulta Tempo y comprueba que los tramos de A (servidor, cliente HTTP, envío a Kafka) y de B (servidor, recepción del mensaje) comparten **un solo `traceId`**, y que la línea de registro de cada servicio lleva ese mismo identificador (patrón del README).

## 3. Romper la propagación con `@Async` y arreglarla

En B, un método `@Async("sinContexto")` (ejecutor sin decorador) corta la traza: la línea de registro del tramo `notificar.roto` no lleva el `traceId` de la petición y su tramo cae en **otra traza** (raíz huérfana). Con `@Async("conContexto")`, cuyo ejecutor lleva `ContextPropagatingTaskDecorator` (`notificar.arreglado`), el `traceId` se conserva en el hilo y en el registro.

## Por qué el README usa `management.opentelemetry.tracing.export.otlp.endpoint`

La clave antigua `management.otlp.tracing.endpoint` ya no existe en Spring Boot 4. Con ella el muestreo funciona (el `traceId` aparece en el registro) pero **no se exporta ninguna traza**: Tempo no recibe nada de ese servicio (lo demuestra la prueba del servicio con la clave vieja). La clave correcta es `management.opentelemetry.tracing.export.otlp.endpoint`, con `spring-boot-starter-opentelemetry`.

## Por qué hacen falta las dos propiedades `observation-enabled` de Kafka

En Kafka la propagación no es automática. La prueba `hallazgo_sin_las_dos_propiedades_de_observacion_de_kafka...` levanta dos servicios con el YAML del README pero con `spring.kafka.template.observation-enabled=false` y `spring.kafka.listener.observation-enabled=false` (el valor por defecto). Resultado en Tempo: la traza contiene los tramos HTTP de A y de B, pero **ninguno** que contenga `pedidos.creados`: ni el envío ni la recepción. Con las dos propiedades en `true` (prueba 1) el envío y la recepción aparecen en la misma traza.

Un detalle de las pruebas: las líneas de registro del pedido 43 se seleccionan buscando `"pedidoId":43`, no `43` a secas, porque un identificador de traza hexadecimal puede contener "43" por azar.

## 4. SLI, SLO y presupuesto

- SLI: porcentaje de `GET /pedidos/{id}` con respuesta 200 en menos de 300 ms.
- SLO: 99,9 % en 30 días.
- Presupuesto de error: 30 x 24 x 60 = 43 200 min; 0,1 % = **43,2 minutos al mes**.
- Medido en la prueba (60 peticiones): SLI 100 %, p95 7 ms.

## 5. Dos alertas por síntomas

`infra/alertas.yml`: `ServicioAErroresAltos` (más del 1 % de 5xx en 5 min, durante 2 min) y `ServicioALentitudAlta` (más del 5 % por encima de 300 ms), cada una con su procedimiento en `annotations.runbook`. `promtool check rules` -> `SUCCESS: 2 rules found`; `promtool test rules` con series sintéticas (`alertas_test.yml`) -> `SUCCESS`; además las expresiones se evaluaron contra las métricas reales que dejó el incidente.

## 6. Incidente y análisis posterior sin culpables

Se introdujo 800 ms de latencia en B (`POST /control?retraso=800`):

| | SLI | p95 |
|---|---|---|
| Normal | 100 % | 7 ms |
| Incidente | 0 % | 810 ms |

Con el SLI en 0 % y SLO 99,9 %, el presupuesto se consume **1000 veces** más rápido de lo sostenible: se agotaría en **0,72 horas**.

### Análisis posterior

- **Qué pasó.** Durante la prueba, `GET /pedidos/{id}` de A pasó de 7 ms a ~810 ms. Sobre las métricas reales, la expresión de `ServicioALentitudAlta` devuelve resultado y la de errores no (las respuestas seguían siendo 200): la alerta correcta es la de latencia. (No se esperó a que el evaluador la disparara; se comprobó la expresión con una consulta a Prometheus y las reglas con `promtool test`.)
- **Cronología.** t0: se introduce la latencia en B. Detección: la expresión de latencia devuelve resultado. Mitigación: se quita la latencia (`retraso=0`), el SLI vuelve a 100 % (p95 7 ms).
- **Localización.** La traza `e0f86388...` de una petición lenta muestra A = 806 ms y, dentro, el tramo de B `http get /inventario/{id}` = 804 ms: B explica el 99,7 % del tiempo. Sin la traza habría que haber mirado ambos servicios.
- **Por qué el sistema lo permitió.** A llama a B sin tiempo límite corto ni cortacircuitos (ver taller 05): una dependencia lenta se convierte en latencia propia.
- **Qué cambiamos.** Tiempo límite y cortacircuitos en la llamada A->B; alerta sobre la latencia de B directamente; ensayo mensual de este mismo incidente. Nadie "causó" el incidente: el diseño no tenía defensa.
