# Análisis del taller 04 (agotamiento del pool)

Pool de 5 (`pedidos`), `connection-timeout` de 3 s, PostgreSQL 17, Spring Boot 4.1.1 con JPA, 20 usuarios y k6. Servicio de pagos de pega
(`herramientas/Externo.java`). Las cifras salen de `resultados/resultados.md`, que escribe `medir.sh` al ejecutarse.

## Pasos 2 y 3: todo dentro de `@Transactional` (v1)

| Pagos tarda | peticiones/s | p95 | errores | activas / pendientes | uso de la conexión | CPU de PostgreSQL |
|---|---|---|---|---|---|---|
| 50 ms | ~87 | ~240 ms | 0 % | 5 / 15 (en el pico) | ~57 ms | ~2 % |
| 2 s | ~5 | ~4,1 s | ~56 % | 5 / 15 | **~1944 ms** | **~0 %** |

Con 2 s el pool de 5 sostiene 5 / 2 s = 2,5 transacciones por segundo por cada "vuelta" de 5 y el resto espera 3 s y falla. Lo que se ve es un
pool lleno (`active=5`), cola (`pending=15`) y un `usage` de casi 2 s **mientras la base no trabaja nada** (CPU ~0 %): la conexión está
retenida por quien llama al servicio externo, no por las consultas (que tardan 1 o 2 ms). Eso es la huella de la retención.

## Paso 4: la cuenta

Conexiones necesarias = peticiones por segundo × tiempo de retención. Para los 20 usuarios que no esperan (≈ 10 peticiones/s con pagos a 2 s) hacen
falta 10 × 2 s = **20 conexiones**; hay 5 (y la base solo admite 27). Con pagos a 50 ms bastan 20 × 0,057 ≈ 1,1. La solución no puede
ser subir el pool: 20 conexiones retenidas esperando a un tercero no son capacidad, son desperdicio.

## Paso 5: transacciones cortas con el mismo pool (v2)

La llamada externa va fuera de cualquier transacción; las dos transacciones (leer, guardar) duran milisegundos. Con pagos a 2 s: **0 % de
errores**, ~9 peticiones/s (el techo de 20 usuarios / 2 s = 10), `usage` de ~3 ms y 0 conexiones activas. El mismo pool de 5 atiende ahora
lo que antes pedía 20.

**Hallazgo del taller:** con JPA y `spring.jpa.open-in-view` en su valor por defecto (`true`) la misma v2 **no mejora nada** (56 % de errores,
`usage` ~1950 ms): la vista abierta mantiene el `EntityManager`, y con él la conexión, durante toda la petición, también durante la llamada
externa. Solo al desactivarla funciona el paso 5. Por eso el enunciado del paso 5 avisa de ello (se añadió una línea al README) y por eso el
paso 7 es el mismo problema visto desde otro lado.

## Paso 6: el guardado falla después del cobro

Con la v2, si el segundo guardado falla (provocado desde la base con una restricción `check` que rechaza confirmar el pedido 7) tras un cobro correcto: respuesta HTTP 500, **un cobro recibido por pagos** y el
pedido sigue `PENDIENTE` en la base. Ya no hay una transacción que lo deshaga todo: el sistema queda inconsistente (cobrado y sin confirmar).
**Cómo se resolvería** (no se implementa): (1) idempotencia: el cobro se hace con una clave por pedido, de modo que reintentar la confirmación
no cobra dos veces; (2) un estado intermedio explícito (`COBRANDO`/`COBRADO`) guardado **antes** de llamar y una tarea que reconcilie los
pedidos `COBRADO` sin confirmar; (3) o una compensación (devolver el cobro) si la confirmación no se puede completar; (4) mejor aún, el patrón
de bandeja de salida/saga de las sesiones de Kafka y sagas. Lo que se pierde al partir la transacción es la atomicidad: se cambia por
consistencia eventual con reglas de recuperación.

## Paso 7: la vista abierta

| Variante | Resultado | Retención de la conexión (máx) |
|---|---|---|
| `open-in-view` por defecto, `/vista` | 200 | **~1040 ms** (el segundo de "construir la respuesta") |
| `open-in-view=false`, `/vista` | **500** `LazyInitializationException ... 'taller.Pedido.lineas' ... (no session)` | ~25 ms |
| `open-in-view=false`, `/vista-corregida` (lectura con `join fetch` en una transacción de solo lectura) | 200 | ~46 ms |

Se rompe todo lo que tocaba una relación perezosa fuera de una transacción (aquí `getLineas()` al armar la respuesta). Se arregla leyendo lo
que la respuesta necesita **dentro** de la transacción (consulta con `join fetch` o proyección) y devolviendo un objeto sin relaciones
perezosas. Decisión: se desactiva (`spring.jpa.open-in-view=false`) en un servicio con pool pequeño, porque con la vista abierta la
retención depende de todo lo que ocurra al construir y escribir la respuesta, que es justo lo que no se controla.

## Paso 8: consulta lenta (sin índice)

`select count(*) ... where cliente_id = ?` sobre 6 millones de filas: **~1 ms con índice, ~540 ms sin él** (`explain analyze`). Con 20 usuarios sin
índice: ~5 peticiones/s, 45 % de errores, pool al máximo y `usage` ~1700 ms, igual que con la llamada externa. **La diferencia** está en la CPU de
PostgreSQL: **~98 %** (con la llamada externa, ~0 %). En las métricas del pool se ven igual; hay que mirar la base (y `explain`).

## Paso 9: bloqueos

Una transacción actualiza la fila 1 y se queda 6 s; llegan 8 actualizaciones de la misma fila. Cada una toma una conexión y **espera el
bloqueo dentro de PostgreSQL**: `pg_stat_activity` muestra 4 sesiones con `wait_event_type = Lock` (las que caben en el pool) y el pool queda
lleno (5 de 5). La CPU de la base es ~0 %. Mientras, una petición a un extremo que no tiene nada que ver (`/historial/1`) espera cola y tarda ~2,4 s
(con 3 s de `connection-timeout` casi falla). Se diferencia de la consulta lenta en que la base está ociosa, y de la llamada externa en que las
sesiones están en `Lock`.

## Paso 10: hilos virtuales (200 usuarios, v1, pagos a 2 s)

| | Hilos de la JVM | Errores | Pool |
|---|---|---|---|
| Hilos de plataforma | ~226 | ~96 % | 5 activas, ~180 pendientes |
| Hilos virtuales (`spring.threads.virtual.enabled=true`) | **~49** | ~96 % | 5 activas, ~195 pendientes |

Cambia el número de hilos del sistema operativo (los virtuales esperan sin ocupar uno), pero el pool se comporta igual: los mismos 5 en uso,
la misma cola y los mismos timeouts. Los hilos virtuales hacen **más barato esperar**, no hacen que la espera sea menos larga; para este
problema no sirven (y pueden empeorarlo: dejan entrar más peticiones a competir por las mismas 5 conexiones).

## Paso 11: la huella de cada causa en las métricas

| Causa | `active` | `pending` | `usage` | CPU de la base | Pista decisiva |
|---|---|---|---|---|---|
| Llamada externa lenta (con la conexión en la mano) | = máx | alto | **largo (≈ la demora externa)** | **~0 %** | `usage` largo con base ociosa; el tiempo de pagos en los registros de la aplicación |
| Vista abierta | sube con el tiempo de respuesta | si hay cola | ≈ duración total de la petición (incluida la serialización) | baja | `usage` ≈ latencia de la petición aunque las consultas sean cortas; el aviso de Boot al arrancar |
| Consulta lenta | = máx | alto | largo (≈ lo que tarda la consulta) | **alta (~98 %)** | CPU y `pg_stat_activity` activas ejecutando; `explain analyze` |
| Bloqueo | = máx | alto | largo | **~0 %** | sesiones en `wait_event_type = Lock` en `pg_stat_activity` |
