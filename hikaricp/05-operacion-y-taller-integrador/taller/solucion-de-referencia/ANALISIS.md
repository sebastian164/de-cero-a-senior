# Taller 05: análisis, guía de guardia y plantilla

Los números de este documento salen de `resultados/resultados.md`, que escribe `medir.sh` al ejecutarse (PostgreSQL 17, `max_connections = 30`, HikariCP 7.1.0, Spring Boot 4.1.1, k6). La configuración con su cuenta está en `src/main/resources/application.yaml`; las alertas, en `alertas.yaml`; el panel, en `panel.json`.

## Parte 1: la cuenta

- Límite de la base: `max_connections = 30`. PostgreSQL reserva 3 para superusuarios (`superuser_reserved_connections`), así que las aplicaciones disponen de 27.
- Reserva de administración: 3 conexiones con la cuenta de la aplicación. Presupuesto: 27 - 3 = 24.
- Instancias: 3 normales. Un despliegue gradual sube una antes de bajar otra (4 a la vez), y el autoescalado tiene tope de 4. Hay que cubrir el caso peor: 4 instancias.
- Pool por instancia: 24 / 4 = **6**. Comprobación: 4 x 6 = 24 <= 27. Con 5 instancias serían 30 > 27: el autoescalado no puede pasar de 4 sin rehacer la cuenta.
- Confirmación con k6: una sola instancia con pool de 6 atiende 300 peticiones/s (el triple de lo que le toca con tres instancias a 200 en total) sin errores ni timeouts. Con una retención de ~2 ms por petición, 300/s ocupan ~0,6 conexiones de media: el 6 sobra para el trabajo normal y sirve de colchón.

## Parte 2: ver

- Panel: las cuatro métricas de la sesión (`hikaricp.connections.active`, `.pending`, `.usage` y `.timeout`) más las conexiones totales y la duración por extremo. Se exporta en `panel.json`.
- Alertas: temprana (`pending > 0` sostenido) y grave (`increase(timeout) > 0`), en `alertas.yaml`. La detección de fugas es `leak-detection-threshold: 2000`, el mínimo que acepta HikariCP (por debajo desactiva la función y lo avisa en el registro; comprobado con la 7.1.0).
- Disponibilidad y vida: `readiness` incluye `db` (pide una conexión al pool y espera como mucho `connection-timeout`, 1 s); `liveness` solo `livenessState`. La vida **no** depende de la base porque reiniciar el proceso no arregla una base caída: Kubernetes mataría y recrearía todas las instancias a la vez, cada una abriría su pool de golpe contra una base que no responde y se perdería lo que sí funciona. Lo comprobado: durante la caída de PostgreSQL la disponibilidad responde 503 y la vida 200, y al volver la base la disponibilidad vuelve sola.

## Parte 3: el simulacro

La tabla con el tiempo hasta el diagnóstico, la métrica que dio la pista y la alerta que sonó, para las siete situaciones, está en `resultados/resultados.md` (sección Parte 3). `herramientas/diagnostico.py` solo lee lo grabado (panel y registro), no sabe qué se provocó.

Cómo se diagnostica (`herramientas/diagnostico.py`): fuga = aviso de `leak-detection` que no se devuelve; retención = avisos que sí se devuelven con cola; capacidad = cola con usos cortos y sin avisos; caída = disponibilidad 503 con rechazos en el registro; conexiones cortadas = `marked as broken`/`Failed to validate` sin cola; demasiadas instancias = la suma de `hikaricp.connections` toca el límite de la base (27) y algún pool no llega a su máximo. La falta de capacidad se provoca con 2000 peticiones/s sobre tres instancias de pool 6 (con solo el doble de la carga normal el pool de 6 no se llenaba: usa 5 ms por petición).

Qué faltó en el primer intento (nivel 1: solo el panel básico y el registro) y se añadió en el segundo (nivel 2):

- Duración por extremo: sin ella, retención por llamada externa y consulta lenta se parecen (activas al máximo, uso alto, pendientes > 0). Con ella se ve qué extremo retiene.
  Medido: la consulta lenta (índice borrado, 5 % de las peticiones al historial de 6 millones de filas) satura la base (CPU de PostgreSQL de 250 % a 370 %) y la consulta de historial pasa de ~3 ms a decenas de ms. Con solo el panel básico el diagnóstico se desvía a `retencion` (conexiones que se retienen y se devuelven: es lo que se ve desde el pool); con la duración por extremo y la CPU de la base, `consulta-lenta`.
- Tasa de peticiones: sin ella, la falta de capacidad (la carga se multiplicó por 10 y el pool, que no cambió, se llena) se confunde con retención en cuanto los avisos de fuga empiezan a saltar por la cola. Con la tasa por extremo se ve que la carga creció y que no se retiene nada más tiempo.
- CPU de la base: separa "la base es lenta" (CPU alta, el pool no tiene culpa) de "el servicio externo es lento" (base ociosa).
- Conexiones totales frente a creadas: distinguen cortes desde fuera (el pool las repone) de falta de capacidad.

Ninguna situación se "resolvió" subiendo el tamaño del pool: la fuga se corrige cerrando la conexión; la retención, sacando la llamada externa de la transacción; la consulta lenta, con el índice; la falta de capacidad, con más instancias dentro de la cuenta (o PgBouncer); las instancias de más, bajando a 4 o con PgBouncer. Subir el pool en cualquiera de ellas solo traslada la cola a la base.

### Las alertas en el simulacro, y qué falta

La alerta grave sonó en 4 de las 7 situaciones (retención 1 s, consulta lenta 2 s, caída 3 s, fuga 19 s) y la temprana solo en la fuga (a los 39 s, después de la grave, que sonó a los 19 s: los timeouts llegan antes de que se cumpla la ventana de la temprana). En capacidad, conexiones cortadas y demasiadas instancias no suena ninguna. Por qué: la alerta temprana exige `pending > 0` durante toda la ventana, y las provocaciones bruscas del simulacro provocan errores en segundos, así que la grave se adelanta; con picos cortos (capacidad, cortes) `pending` oscila y no se mantiene. La lección dice que la temprana avisa antes de los errores: eso vale para degradaciones lentas (la fuga en producción tarda horas, no 60 s); no se demostró con estas provocaciones. Falta una alerta para "demasiadas instancias": `increase` de los rechazos de PostgreSQL (`FATAL: remaining connection slots are reserved...`), o la suma de `hikaricp_connections` de todas las instancias frente al límite (`sum(hikaricp_connections) > 0.8 * max_connections`). En la caída de la base la grave suena, pero sola no dice que es la base; para eso hace falta la disponibilidad (503).

Dos datos que pide la tabla del enunciado: en la fuga el registro dice la línea (`at taller.Extremos.fuga(Extremos.java:65)`, la primera de la aplicación en la traza de `leak-detection`), y al cortar las sesiones desde PostgreSQL el usuario vio 11 peticiones con error de 12 001 (0,09 %), y el pool respondió reponiendo las conexiones solo.

### Alertas y panel contra Prometheus y Grafana reales (`prometheus.sh`)

Con `micrometer-registry-prometheus`, `management.endpoints.web.exposure.include: ...prometheus` y `management.metrics.distribution.percentiles-histogram.hikaricp.connections: true`:

- `promtool check rules alertas.yaml`: SUCCESS, 2 rules found (Prometheus 3.16.0).
- Un Prometheus real leyendo `/actuator/prometheus` cada segundo, con el servicio externo a 3 s y 150 peticiones/s: la alerta grave pasó a `firing` a los 3 s y la temprana a los 62 s (su ventana es de 1 minuto).
- Las 13 series que usan las alertas y el panel existen con esos nombres (`hikaricp_connections_timeout_total`, `..._acquire_seconds_bucket`, etc.). El p95 de `usage` salió 3,04 s (la retención) y el de `acquire` 1,07 s (el `connection-timeout` de 1 s).
- Grafana 13.3.0 importó `panel.json` por su API, las consultas de sus cuatro paneles devolvieron series y el JSON exportado (`resultados/prometheus/panel-exportado.json`) conserva los paneles y los dos percentiles 95, de acquire y de usage.

## Parte 4: decidir

### PgBouncer (1.26.1, modo transacción, `default_pool_size` 15)

Seis instancias con pool de 6 son 36 conexiones de cliente, más de las 27 que admite la base directamente; detrás de PgBouncer funcionan sin errores con 15 conexiones reales como máximo. Qué cambia: el límite de la base deja de ser la suma de los pools. Qué dejó de funcionar: todo lo que vive en la sesión. Se comprobó con `pg_advisory_lock`, que en modo transacción queda retenido en la conexión del servidor y lo hereda el siguiente cliente. Lo mismo vale para `SET` de sesión, `LISTEN/NOTIFY`, cursores `WITH HOLD` y los *prepared statements* de servidor. Esto último se comprobó con ocho clientes concurrentes y `prepareThreshold=1`: con `max_prepared_statements = 0` falla (`prepared statement "S_1" already exists`); con el valor por defecto de PgBouncer 1.26.1 (200) los 2400 intentos funcionan. Antes de la 1.21 no existía ese soporte: hay que desactivarlos en el controlador (`prepareThreshold=0`).

### Guía de guardia del pool

| Alerta | Qué significa | Primer paso |
|---|---|---|
| `PoolConHilosEnEspera` (temprana) | Hay peticiones esperando conexión. Todavía no hay errores. | Mira `active` y `usage`. Activas al máximo y `usage` largo: retención o fuga. Activas bajas y base con CPU alta: consulta lenta. |
| `PoolSinConexionesATiempo` (grave) | Hay usuarios con error: se agotó `connection-timeout`. | Lee el registro: el mensaje trae `total, active, idle, waiting`. Comprueba en la base `pg_stat_activity` (qué hacen las sesiones de la aplicación y desde cuándo). |
| Aviso `Connection leak detection triggered` | Una conexión lleva más del umbral fuera del pool. | Lee la traza: la primera línea de nuestro código es donde se tomó. Si luego aparece `was returned to the pool (unleaked)`, no era fuga, era una operación lenta. |
| `readiness` en 503 | La aplicación no consigue conexión en 1 s. | Mira si la base responde. No reinicies las aplicaciones: salen de la rotación solas y vuelven solas. |

**Lo que NO se hace**

- **NO SUBIR `maximum-pool-size` para que pase el rato.** Casi siempre empeora: más conexiones sobre una base que ya va lenta, y se puede agotar `max_connections` y tumbar a las otras aplicaciones.
- **NO REINICIAR todas las instancias a la vez**: abren sus pools de golpe contra una base que se está recuperando.
- **NO matar sesiones en la base sin mirar de quién son**: el pool las repone, pero la transacción en vuelo se pierde.
- **NO subir `connection-timeout`** para que el error desaparezca: solo hace esperar más a más usuarios y llena el servidor de hilos.
- **NO activar el reintento ciego** de las peticiones que fallan: multiplica la carga justo cuando falta capacidad.
- **NO desactivar `leak-detection-threshold`** porque "da falsos positivos": se sube el umbral por encima de la operación legítima más larga.

### Plantilla de configuración del equipo

```yaml
spring:
  datasource:
    hikari:
      pool-name: <nombre-del-servicio>          # sale en métricas y registros
      maximum-pool-size: <N>                    # N = presupuesto de conexiones del servicio / instancias máximas (despliegue y autoescalado incluidos)
      minimum-idle: <N>                         # tamaño fijo, salvo decisión escrita en contrario
      connection-timeout: 1000                  # falla rápido
      max-lifetime: 1500000                     # 25 min: menos que cualquier corte de red o de la base
      keepalive-time: 120000
      leak-detection-threshold: <mayor que la operación legítima más larga, mínimo 2000>
management:
  endpoint:
    health:
      group:
        liveness:  { include: livenessState }          # nunca la base
        readiness: { include: "readinessState, db" }
```

La cuenta que cada servicio rellena en su README:

```
límite de la base ............................ L   (max_connections)
reserva de superusuario ...................... S   (superuser_reserved_connections, por defecto 3)
reserva de administración .................... A
presupuesto del servicio ..................... P = (L - S - A) x fracción que le toca
instancias máximas (despliegue + autoescalado) I
maximum-pool-size ............................ P / I, redondeado hacia abajo
comprobación ................................. I x tamaño <= L - S - A
```
