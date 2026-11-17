# 02 — Trazas, métricas y objetivos de servicio

Esta sesión usa OpenTelemetry, el estándar abierto para generar y enviar telemetría. Las órdenes y la configuración se ejecutaron con el agente de OpenTelemetry para Java 2.33.1 y `grafana/otel-lgtm:0.40.0`. OpenTelemetry evoluciona rápido; ante cualquier diferencia, manda su documentación.

## Idea

El rastreo de un paquete. No te dice solo "entregado" o "no entregado": te muestra cada tramo, con la hora de entrada y de salida de cada bodega. Si tardó cinco días, ves que cuatro los pasó parado en una sola. Una traza es eso para una petición. Y el compromiso de la empresa de mensajería —"el 95 % de los paquetes llega en dos días"— es un objetivo de servicio: no promete que todos lleguen a tiempo, promete cuántos.

## Lo esencial

### Trazas

| Concepto | Qué es |
|---|---|
| **Traza** | El recorrido completo de una petición, a través de todos los servicios |
| **Tramo** (*span*) | Una operación dentro de la traza: una llamada HTTP, una consulta, un método. Tiene inicio, duración, atributos y estado |
| **Contexto** | El identificador de la traza y del tramo actual, que viaja con la petición |
| **Propagación** | Pasar ese contexto al servicio siguiente, normalmente en la cabecera `traceparent` |

Si un servicio no propaga el contexto, la traza se parte en dos y pierdes justo lo que querías ver.

### Métricas

| Tipo | Para qué | Ejemplo |
|---|---|---|
| Contador | Algo que solo sube | Peticiones atendidas, errores |
| Histograma | Cómo se reparten los valores | Duración de las peticiones |
| Medidor | Un valor que sube y baja | Conexiones abiertas, memoria en uso |

Las medias mienten: una media de 200 ms puede esconder que uno de cada veinte usuarios espera cinco segundos. Se miran **percentiles** (p50, p95, p99), y para eso hacen falta histogramas.

Para un servicio que atiende peticiones, tres medidas dan casi todo: **tasa** (cuántas), **errores** (cuántas fallan) y **duración** (cuánto tardan).

### Cardinalidad

Cada combinación distinta de valores de las etiquetas de una métrica es una serie que hay que guardar. El medio de pago (cinco valores) es una buena etiqueta. El identificador del usuario (millones) revienta el sistema de métricas y la factura. Lo que tiene muchos valores va en las trazas y en los eventos, no en las etiquetas de una métrica.

### Objetivos de servicio

| Término | Qué es | Ejemplo |
|---|---|---|
| **Indicador** (SLI) | Una medida de lo que vive el usuario | Proporción de peticiones que responden bien en menos de 300 ms |
| **Objetivo** (SLO) | La meta para ese indicador | 99,5 % en 30 días |
| **Presupuesto de error** | Lo que el objetivo permite fallar | 0,5 %: unas 3,6 horas al mes |

El presupuesto convierte una discusión de opiniones en una de datos: si queda presupuesto, se puede arriesgar y desplegar; si se agotó, se prioriza la estabilidad. Y las alertas se ponen sobre el **ritmo al que se gasta**, no sobre síntomas sueltos como la CPU.

### Cómo se instrumenta

| Forma | Qué hace | Cuándo |
|---|---|---|
| Automática (un agente) | Instrumenta bibliotecas conocidas sin tocar el código: servidores HTTP, clientes, bases de datos | Siempre, como base |
| Manual (la API) | Tú creas tramos, atributos y métricas del negocio | Para lo que solo tú sabes que importa |

La telemetría sale por un protocolo estándar (OTLP) hacia un **recolector**, que la procesa y la reenvía a donde se guarde. Tu código no conoce el destino final: cambiar de proveedor no toca la aplicación.

## Entorno

Una sola imagen trae un recolector y dónde ver registros, métricas y trazas, pensada para desarrollo local:

<!-- file: entorno/compose.yaml -->
```yaml
# Un entorno local de observabilidad en un contenedor. Solo para desarrollo.
# Elige la etiqueta vigente de la imagen y pásala al arrancar:
#   VERSION_LGTM=<etiqueta> docker compose up -d
services:
  observabilidad:
    image: grafana/otel-lgtm:${VERSION_LGTM:?define VERSION_LGTM con la etiqueta vigente de la imagen}
    ports:
      - "3000:3000"     # la interfaz web
      - "4317:4317"     # entrada de telemetría OTLP por gRPC
      - "4318:4318"     # entrada de telemetría OTLP por HTTP
```

Y la aplicación le envía su telemetría con variables de entorno estándar:

```bash
export OTEL_SERVICE_NAME=bitacora-api
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
javac -d clases Servidor.java
java -javaagent:opentelemetry-javaagent.jar -cp clases Servidor
```

Se compila antes porque, con `java Servidor.java`, el agente cree que lo lanza una herramienta del JDK y no se activa: lo avisa al arrancar con "agent will not be started".

El archivo de Compose se levantó con Docker Compose y las trazas y métricas se consultaron desde Grafana.

## Ejemplo

### Antes

Un tablero con la CPU, la memoria y el número de pods. Una alerta: "CPU por encima del 80 %". Un viernes, los usuarios reportan que guardar una nota tarda diez segundos. La CPU está al 12 %. El tablero está en verde. Nadie sabe por dónde empezar, y alguien propone reiniciar.

### Análisis

| Problema | Consecuencia |
|---|---|
| Se mide la máquina, no la experiencia del usuario | El sistema puede estar "sano" y ser inservible |
| La alerta vigila una causa posible, no el síntoma | No suena cuando el usuario sufre, y suena cuando no pasa nada |
| No hay trazas | No se sabe en qué parte del recorrido se van los diez segundos |
| No hay un objetivo acordado | Nadie puede decir si diez segundos es un incidente o "lo normal en fin de mes" |

### Después

Lo que se define **antes** de programar la funcionalidad:

| Elemento | Definición |
|---|---|
| Indicador | Proporción de `POST /api/notas` que responden 201 en menos de 500 ms |
| Objetivo | 99 % en ventanas de 7 días |
| Presupuesto | 1 %: de 10 000 peticiones, 100 pueden fallar o tardar |
| Alerta | El presupuesto se está gastando a un ritmo que lo agotaría en un día |
| Tramos | El que crea el agente para la petición, más uno manual para la escritura en disco |
| Atributos | Tamaño de la nota, resultado, versión. **No** el contenido |
| Métrica de negocio | Contador de notas guardadas, con la etiqueta `resultado` |

Con eso, el mismo viernes es distinto: la alerta suena porque el presupuesto se quema, la traza de una petición lenta muestra que nueve segundos y medio están en el tramo de escritura en disco, y el atributo de versión dice que empezó con el despliegue de la mañana.

## Errores comunes

- Alertar sobre CPU y memoria en lugar de sobre lo que vive el usuario.
- Medias en vez de percentiles.
- Etiquetas de métricas con identificadores de usuario o de pedido.
- No propagar el contexto y tener trazas partidas.
- Quedarse con la instrumentación automática y no añadir nada del negocio.
- Objetivos del 100 %: imposibles, y no dejan margen para desplegar.
- Objetivos que nadie acordó con el negocio.
- Guardar todas las trazas de todo, sin muestreo, y descubrirlo en la factura.

## Taller

**Objetivo:** añadir trazas, métricas y un objetivo de servicio a Bitácora, y ver su comportamiento con datos.

1. Levanta el entorno con la etiqueta vigente de la imagen. Abre la interfaz.
2. Descarga el agente de OpenTelemetry para Java siguiendo su documentación y arranca la API con él. Genera tráfico. ¿Aparecen trazas del servidor HTTP? Si tu versión del agente no instrumenta el servidor HTTP del JDK, no cambies de servidor: crea los tramos a mano con la API. Documenta lo que encontraste.
3. Añade un tramo manual alrededor de la lectura y otro alrededor de la escritura del archivo de notas, con atributos útiles y sin datos sensibles.
4. Pon el nginx de la web delante y consigue una sola traza que cruce los dos. Si se parte, averigua qué falta propagar.
5. Une señales: el identificador de la traza debe aparecer en tus eventos de la sesión 01, para saltar del registro a la traza.
6. Crea métricas: tasa, errores y un histograma de duración por ruta, más un contador de negocio de notas guardadas.
7. Añade a propósito una etiqueta de alta cardinalidad (el texto de la nota). Genera tráfico variado y observa cuántas series aparecen. Quítala y explica el problema.
8. Compara la media con el p95 y el p99 de `/api/carga` llamándola con valores variados. ¿Qué historia cuenta cada uno?
9. Define un indicador y un objetivo para "guardar una nota" y otro para "leer notas". Justifica los números: ¿por qué no 100 %?
10. Calcula el presupuesto de error en peticiones y en tiempo. Construye un panel que muestre cuánto queda.
11. Introduce un defecto: una de cada diez escrituras tarda dos segundos. Usando solo la interfaz, encuentra qué operación es lenta, desde cuándo y qué tienen en común las peticiones afectadas.
12. Escribe la regla de alerta sobre el ritmo de gasto del presupuesto, aunque no la despliegues, y explica por qué es mejor que "p95 mayor que 500 ms".

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Una traza cruza nginx y la API | Captura de la traza |
| Del registro se llega a la traza | El identificador en ambos |
| Hay métricas de tasa, errores y duración por ruta | El panel |
| Los objetivos están justificados | `ANALISIS.md` |
| El defecto se encuentra solo con la interfaz | Paso 11, con las consultas usadas |

**Entrega:** rama `taller/odd-02`, con el código, el Compose y los paneles exportados en `taller/solucion/`, y `ANALISIS.md`.

## En el trabajo real

En Spring Boot, buena parte de esto viene hecho: Actuator y Micrometer exponen métricas y trazas, y el agente de OpenTelemetry instrumenta lo demás. Lo que no viene hecho, y es tu trabajo, son los atributos del negocio, los objetivos y las alertas. Y un aviso de costos: la telemetría se paga por volumen, y una etiqueta mal elegida puede multiplicar la factura.

## Preguntas senior

1. ¿Qué es una traza, qué es un tramo y qué pasa si no se propaga el contexto?
2. ¿Por qué percentiles y no medias?
3. ¿Qué es la cardinalidad y por qué importa?
4. Explica indicador, objetivo y presupuesto de error, y para qué sirve el presupuesto.
5. ¿Por qué alertar sobre síntomas del usuario y no sobre causas?
