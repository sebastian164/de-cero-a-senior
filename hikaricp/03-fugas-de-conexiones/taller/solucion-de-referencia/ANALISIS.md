# Análisis del taller 03 (fugas de conexiones)

Pool de 5 (`clientes`), 25 peticiones/s con una de cada 50 por el camino defectuoso (id negativo). Al ejecutar `medir.sh` los datos quedan en `resultados/resultados.md`,
las series de conexiones activas en `resultados/*.tsv` y la gráfica en `resultados/grafica-activas.svg`.

## Pasos 2 y 3: dónde y cuándo falla

Las conexiones activas suben **en escalera**: una en la petición 50, otra en la 100, y así hasta cinco en la 250. Entonces el pool queda
vacío y la petición **251** (un id positivo, inocente) espera `connectionTimeout` y falla con el mensaje completo de HikariCP:

    clientes - Connection is not available, request timed out after 2006ms (total=5, active=5, idle=0, waiting=49)

Ninguna de las peticiones que fallan pasa por el camino defectuoso: la que falla es la víctima, no la culpable. Por eso no se encuentra la
fuga mirando la petición que falla ni leyendo el mensaje (que solo dice que el pool está vacío: `active=5, idle=0`). Al parar el tráfico las
activas se quedan en 5 y nunca bajan: es la firma de una fuga.

## Paso 4: con un pool de 20

El primer fallo llega en la petición **1001**, cuatro veces más tarde (20 / 5): el pool más grande solo tarda más en vaciarse. El mismo
tráfico acaba en el mismo mensaje. Subir el pool no arregla una fuga: retrasa el síntoma, lo hace más difícil de relacionar con el
despliegue que lo causó y se come las conexiones de la base (aquí 20 de 27).

## Paso 5: `leak-detection-threshold` = 2000 ms

El aviso sale 2 s después de que una conexión se toma y no vuelve, y trae una traza de **quien la pidió**:

    Connection leak detection triggered for org.postgresql.jdbc.PgConnection@... on thread http-nio-46303-exec-N, stack trace follows
    java.lang.Exception: Apparent connection leak detected
        at com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:127)
        at taller.Clientes.conFuga(Clientes.java:32)

La primera línea nuestra es `Clientes.java:32`, el `dataSource.getConnection()` de `conFuga`: sin leer el código se sabe qué método toma la conexión
y no la devuelve. La traza señala dónde se **pidió**, no por qué no se devolvió: hay que mirar los caminos de salida de ese método (el
`return` con id negativo).

## Paso 6: `pg_stat_activity` durante la fuga

Las cinco conexiones fugadas están en estado **`idle`**, con la última consulta `select nombre from cliente where id = $1` terminada hace
decenas de segundos. Para PostgreSQL no hacen nada: la fuga está en la aplicación, que las tiene "prestadas" y no las usa. No hay
transacción abierta (autocommit), así que no retienen bloqueos; con una transacción abierta estarían `idle in transaction`.

## Paso 7: corrección con `try-with-resources`

Con `/corregido/clientes` el mismo tráfico no produce errores y las activas vuelven a **0** durante el reposo al final.

## Paso 8: transacción a medias, con `close()`

Un camino que hizo `setAutoCommit(false)` y salió sin `commit()` ni `rollback()`, pero cerrando la conexión: **no hay fuga** (activas 0). HikariCP
detecta el estado sucio al devolver la conexión y **revierte** (`Executed rollback on connection ... due to dirty commit state on close()`):
la fila 3 queda como estaba (`visitas=0`) y la 4, que sí confirmó, tiene 1. El bloqueo de fila que tomó el `update` se libera con la reversión
(otra sesión actualiza la fila 3 sin esperar). Lo que se pierde es el trabajo no confirmado, sin ruido.

## Paso 9: una falsa alarma

Una operación legítima de 3 s con el umbral a 2 s produce el mismo aviso `Connection leak detection triggered`, pero después aparece
`Previously reported leaked connection ... was returned to the pool (unleaked)`. Se distingue así: una fuga de verdad **nunca** tiene el
segundo mensaje. Además, el umbral debe ser mayor que la operación legítima más larga del servicio.

## Paso 10: `JdbcTemplate`

La gestión de la conexión quedó dentro de la plantilla: toma la conexión, ejecuta, cierra los `ResultSet` y las sentencias y la devuelve en un
`finally`. Con `JdbcTemplate` (`/plantilla/clientes`) no hay errores ni conexiones fuera. El código de la aplicación ya no puede olvidarla.

## Paso 11: la prueba que detecta la fuga

`SinFugasTest` ejecuta cada extremo corregido por todos sus caminos (id válido, inexistente, negativo) y comprueba que
`hikaricp.connections.active` termina en cero. `DetectaFugaTest` ejecuta el extremo con fuga y comprueba que esa misma comprobación **falla**
(activas ≠ 0): demuestra que la prueba fallaría si la fuga volviera.
