# Taller 05: pedidos con R2DBC y PostgreSQL

PostgreSQL 18.7, Spring Boot 4.1.2, r2dbc-postgresql 1.1.3, base `pedidos05` en el PostgreSQL del entorno del curso (puerto 5433). Dos proyectos: `r2dbc/` (sin JDBC ni JPA: sus dependencias son `spring-boot-starter-webflux`, `spring-boot-starter-data-r2dbc` y `r2dbc-postgresql`) y `jdbc/` (`JdbcTemplate` llamado directamente en el manejador). Esquema en `esquema.sql`: `pedido`, `linea_pedido`, `existencias` (con `check (cantidad >= 0)`), `clave_idempotencia`. Pruebas: `mvn test` en `r2dbc/` (8 pruebas contra el PostgreSQL real).

## DatabaseClient en vez de repositorios (paso 2)
Descontar existencias exige `update existencias set cantidad = cantidad - :q where producto = :p and cantidad >= :q` (atómico, una sola sentencia, devuelve filas afectadas). Un repositorio reactivo haría leer-modificar-guardar, que con concurrencia pierde actualizaciones. Por eso `DatabaseClient`.

## Atomicidad (pasos 3 y 4)
`TransactionalOperator.transactional(...)` envuelve la confirmación. El pedido m1 pide prod-1, prod-2 y prod-3 (se dejó prod-3 en 0):

    antes:   prod-1 1000000 | prod-2 1000000 | prod-3 0 | m1 PENDIENTE
    POST /pedidos/m1/confirmar -> 409 {"detail":"sin existencias de prod-3",...}
    después: prod-1 1000000 | prod-2 1000000 | prod-3 0 | m1 PENDIENTE

Los descuentos de prod-1 y prod-2 y el cambio de estado se revirtieron. También está en la prueba `atomico_elTercerProductoSinExistenciasNoDejaRastro`.

## Rendimiento (paso 5): 40 confirmaciones simultáneas, 4 hilos de bucle, pool de 10 en las dos, 100 ms de `pg_sleep` dentro de la transacción (consulta lenta artificial: sin ella las consultas duran microsegundos y no se ve la diferencia)
| Versión | Total | Media por confirmación | `GET /ping` durante la carga |
|---|---|---|---|
| JDBC en el manejador | 1192 ms | 622 ms | media 86 ms, máx 1063 ms |
| R2DBC | 462 ms | 284 ms | media 2,0 ms, máx 8 ms |
JDBC bloquea el hilo del bucle mientras espera a la base: con 4 hilos solo 4 confirmaciones avanzan a la vez (40/4 x ~100 ms = 1 s) aunque el pool tenga 10 conexiones, y el `ping` queda en cola detrás. R2DBC no retiene hilos: el límite es el pool (40/10 x 100 ms = 400 ms) y el bucle sigue libre.

## Pool de 5 (paso 6)
(`spring.r2dbc.pool.max-size=5` exige también `initial-size=5`: con el `initial-size` por omisión, 10, la aplicación no arranca: `maxSize must be greater than or equal to initialSize`.) La carga de 40 confirmaciones tarda ~1 s (40/5 x 100 ms = 800 ms más los bloqueos de fila): ahora limita el pool.
Lo que se afirma es lo que de verdad enseña el paso, y se mide así: `pg_stat_activity` se muestrea en bucle (una instantánea cada ~100 ms) durante TODA la carga, no una sola foto. Resultado: **el máximo de conexiones con `application_name = 'r2dbc'` es 5 y nunca más**, con 40 peticiones en vuelo. Las 35 peticiones restantes esperan en la cola del pool dentro de la aplicación, sin hilo.
Qué hacen esas 5 conexiones en cada instantánea no es constante, y no hay por qué esperar que lo sea: en las muestras, casi siempre `active` con `wait_event_type = Timeout` (el `pg_sleep` de 100 ms de la consulta lenta artificial), alguna `idle in transaction` (normal aquí: la transacción sigue abierta y el servidor espera a que la aplicación reactiva envíe la siguiente sentencia) y alguna `idle` (conexión devuelta al pool entre dos usos). En otras ejecuciones también salió `active` con `Lock`: las 40 confirmaciones comparten `prod-1`, `prod-2` y `prod-3`, así que el `update existencias` de una puede esperar a la transacción anterior. Por eso no conviene afirmar que todas estén `active` con `Timeout`: mide el máximo de conexiones durante toda la carga, no una sola foto. El ping sigue en milisegundos: el bucle libre.

## Última unidad (paso 7)
Dos pedidos distintos piden la última unidad de `t-ultima` a la vez (`laUltimaUnidadSeVendeUnaVez`): uno recibe 200 y el otro 409, existencias quedan en 0. Lo garantiza el `update ... where cantidad >= :q` (la segunda transacción espera el bloqueo de fila, ve 0 y no actualiza) y, como red de seguridad, el `check (cantidad >= 0)`. La clave de idempotencia (`Idempotency-Key`) evita descontar dos veces ante un reintento. Ojo con qué prueba qué: `laMismaClaveNoDescuentaDosVeces` solo comprueba que la segunda llamada encuentra el pedido ya `CONFIRMADO` (no toca la clave); la rama de la clave repetida se cubre con dos pruebas más: `unaClaveYaProcesadaDevuelveREPETIDAYNoDescuenta` (la clave ya está en la tabla: respuesta `REPETIDA`, existencias y estado intactos, una sola fila de clave) y `dosPeticionesALaVezConLaMismaClaveDescuentanUnaVez` (dos peticiones simultáneas: una confirma, existencias descontadas una vez, una fila de clave). Esa prueba cubre un defecto fácil de cometer: tragarse el error dentro de la transacción. Si el `onErrorResume(DuplicateKeyException)` está *dentro* de la transacción, tragarse un error de PostgreSQL dentro de una transacción la deja abortada, así que el `commit` falla con `PessimisticLockingFailureException` y la respuesta es un 500 en vez de `REPETIDA`. Por eso el error se atiende fuera de `transactional(...)`: la transacción se revierte y se responde `REPETIDA`. Un pedido `RECHAZADO` responde `RECHAZADO` y no `YA_CONFIRMADO` (`unPedidoRechazadoNoSeLlamaYaConfirmado`).
