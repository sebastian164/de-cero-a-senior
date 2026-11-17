# Taller ODD 02: trazas, métricas y objetivos

`comprobar.sh` levanta el entorno, ejecuta cada paso y consulta **solo a través de la interfaz de Grafana** (Prometheus y Tempo). Los números de abajo son de su última ejecución.

## Versiones

| Pieza | Versión |
|---|---|
| Entorno (README) | `grafana/otel-lgtm:0.40.0` (Grafana 13.3.0). Cuando se hizo esta solución había una 0.41.0 más reciente; se usó la 0.40.0, la que fija el README de la sesión (`VERSION_LGTM` la cambia) |
| Agente | OpenTelemetry Java agent 2.33.1 (descargado de las versiones de GitHub, `opentelemetry-javaagent.jar`) |
| API para los tramos y métricas manuales | `opentelemetry-api`, `-context` y `-common` 1.67.0 (la que acompaña a ese agente según el BOM de `opentelemetry-instrumentation-bom` 2.33.1). Con la API 1.68.0 la aplicación falló: `NoClassDefFoundError: io/opentelemetry/common/ComponentLoader` porque faltaba el jar `opentelemetry-common`; con Maven se resuelve solo, a mano hay que añadirlo |
| nginx con trazas | `nginx:stable-alpine-otel` (nginx 1.30.6, `ngx_otel_module`) |
| JDK | 21 |

## Paso 2: ¿instrumenta el agente el servidor HTTP del JDK?

**Sí**, el agente 2.33.1: crea el tramo `POST /api/notas` (ámbito `io.opentelemetry.java-http-server`) sin tocar el código, siempre que se arranque con `javac` + `java -cp` y no con `java Servidor.java` (ver la nota ya corregida en el README). No hizo falta crear los tramos de servidor a mano; los manuales (paso 3) son un añadido.

## Paso 3: tramos manuales

`notas.leer` (atributo `bitacora.notas.total`) y `notas.escribir` (atributos `bitacora.nota.longitud`, `bitacora.escritura.secuencia`, `bitacora.notas.total`). Sin contenido de la nota; el script comprueba que ningún atributo se llama `texto` o `contenido`.

## Paso 4: una traza que cruza nginx y la API

nginx de código abierto no genera trazas; con el módulo (`nginx:stable-alpine-otel`, `otel_trace on; otel_trace_context propagate;`) sí. Una sola traza con `nginx-web` → `POST /api/notas` (API) → `notas.escribir`: el tramo del servidor de la API es hijo del de nginx. Sin el módulo, nginx reenviaría la cabecera `traceparent` pero no crearía su tramo, así que la traza empezaría en la API. Dos tropiezos reales: (1) nginx tarda en resolver `host.docker.internal` por IPv4 e IPv6 y la IPv6 no responde en Docker Desktop (502 intermitentes): se arregla con `resolver 127.0.0.11 ipv6=off` y `proxy_pass` con variable; (2) hasta que Tempo y el recolector están listos se pierden las primeras trazas, aunque Grafana ya diga "sano": el script espera al recolector (`POST /v1/traces` → 200) y a `tempo/ready`.

## Paso 5: del registro a la traza

Cada evento JSON de petición lleva `trace_id` y `span_id` (de `Span.current()`); el `trace_id` de una línea del registro abre la traza en Tempo (3 tramos para un `POST`).

## Paso 6: métricas

`bitacora_peticiones_total{ruta,metodo,clase_estado}` (tasa y errores), `bitacora_duracion_milliseconds_*{ruta,metodo,clase_estado}` (histograma, cubetas por defecto del SDK: 0, 5, 10, 25, 50, 75, 100, 250, 500, 750, 1000, 2500, …) y `bitacora_notas_guardadas_total{resultado}` (negocio). Los rechazos aparecen como `4xx`, separados de los `5xx`. La ruta es la del contexto registrado, y "desconocida" para el resto, para no crear una serie por cada URL inventada.

## Paso 7: alta cardinalidad

Con `texto_nota` como etiqueta, 150 notas distintas crearon **150 series** de `bitacora_notas_guardadas_total`; sin ella, 1 (`resultado="ok"`). Cada nota nueva es una serie nueva que Prometheus guarda para siempre, indexa y consulta; con mil notas por hora y un mes de retención son 720 000 series para contar... notas. El texto es dato para un evento o un atributo de una traza (donde no se agrega), no para una etiqueta de métrica.

## Paso 8: media, p95 y p99

100 llamadas a `/api/carga`: 90 de 10 ms, 8 de 300 ms, 2 de 2000 ms. **Media 74 ms, p50 18 ms, p95 406 ms, p99 1750 ms.** La media dice "todo va bien, 74 ms"; el p95 dice que una de cada veinte espera más de 400 ms; el p99, que una de cada cien espera casi dos segundos. (Los percentiles salen de cubetas y se interpolan: 406 y 1750 son aproximaciones dentro de la cubeta de 250 a 500 y de 1000 a 2500.)

## Pasos 9 y 10: indicador, objetivo y presupuesto

| | Guardar una nota | Leer notas |
|---|---|---|
| Indicador | Proporción de `POST /api/notas` con 2xx y ≤ 500 ms | Proporción de `GET /api/notas` con 2xx y ≤ 250 ms |
| Objetivo | 99 % en ventanas de 7 días | 99,5 % en 30 días |
| Presupuesto | 1 %: 100 de cada 10 000 peticiones; 100,8 minutos en 7 días | 0,5 %: 50 de cada 10 000; 3,6 horas en 30 días |
| Por qué ese número | Guardar es lo que más duele perder y toca disco: más estricto que 95 %, pero un 99,9 % (10 minutos por semana) no deja margen ni para un despliegue | Leer es más frecuente y más barato, y el usuario lo percibe más: un objetivo más alto y una latencia más baja |

**Por qué no 100 %**: es imposible de cumplir (el disco, la red, un despliegue), y no deja presupuesto para desplegar ni para tolerar un arranque lento. Un objetivo del 100 % convierte cada despliegue en un incumplimiento.

Medido en la ejecución: 99,97 % de las escrituras buenas en la última hora (presupuesto casi intacto). El panel (`panel-presupuesto.json`, importado en Grafana por su API) tiene seis paneles: tasa por ruta, errores 5xx, p95 por ruta, SLI de guardar, presupuesto que queda (`1 - (1 - SLI) / 0,01`, recortado a 0) y notas guardadas. Se comprobó que cada consulta del panel devuelve datos (excepto la de 5xx, que no tiene nada que mostrar si no hay 5xx).

## Paso 11: encontrar la escritura lenta solo con la interfaz

Defecto: una de cada diez escrituras tarda 2 s (`DEFECTO_ESCRITURA_LENTA_CADA=10`). Consultas usadas, todas por la interfaz:

1. Panel (Prometheus): `p95` por ruta del servicio: `/api/notas` ≈ 1975 ms. Dice **qué ruta**.
2. Trazas (TraceQL): `{ resource.service.name="bitacora-api-v2" && duration > 1s }`: todas son `POST /api/notas`. Dice **qué operación**.
3. `{ ... && name = "notas.escribir" && duration > 1s }` y abrir cada traza: el tiempo está en el tramo `notas.escribir`, y **su atributo `bitacora.escritura.secuencia` vale 10, 20, 30 y 40**. Dice **qué tienen en común**: una de cada diez, no la longitud ni el contenido.
4. **Desde cuándo**: la hora de inicio de la primera traza lenta (y, en el panel, el momento en que el p95 deja de ser plano).

## Paso 12: la alerta

`alerta-presupuesto.yaml` (validado con `promtool check rules` del propio Prometheus del entorno: 4 reglas). Alerta rápida: ritmo de gasto de 14,4 veces el permitido en 1 h **y** en 5 min (consumiría el 8,6 % del presupuesto semanal en esa hora); alerta lenta: 6 veces en 6 h y 30 min (21 %). Con una de cada diez escrituras lentas el ritmo teórico es 10 veces el permitido; el medido varía entre ejecuciones (14,3 en una y 18,2 en otra: con solo 40 peticiones, `increase` extrapola y sobrestima), siempre entre 10 y 20: sobrepasa el umbral de 6 (la alerta lenta, de ticket, sonaría) y queda a uno u otro lado del de 14,4 (la de página); `comprobar.sh` comprueba ese rango (más de 6 y menos de 25), no un valor exacto. La conclusión es la que importa: una de cada diez lentas **no** es una emergencia de página sostenida en volumen real (10x), pero sí un ticket. Es mejor que "p95 > 500 ms" porque: (1) habla del usuario y del presupuesto, no de un síntoma: una de cada diez lentas ya hace saltar un p95, pero un p95 alto durante 30 segundos de un arranque no debería despertar a nadie; (2) las dos ventanas distinguen un pico de un problema sostenido; (3) su urgencia depende de cuánto presupuesto queda y a qué ritmo se gasta, no de un número arbitrario; (4) un p95 mide una sola cola y no dice si alcanzará para el mes.
