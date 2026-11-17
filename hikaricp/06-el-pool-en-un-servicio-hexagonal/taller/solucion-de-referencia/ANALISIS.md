# Taller 06: el pool debajo del servicio de pedidos

Se resolvió sobre el servicio de pedidos del módulo hexagonal (la solución de referencia del taller 08, `hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia`). Su dominio y su aplicación se compilan desde donde están: no se copiaron ni se modificaron (`ArquitecturaDelPoolTest` lo comprueba, y las huellas SHA-256 de sus 26 archivos quedan en `resultados/huellas-dominio-y-aplicacion.txt`). Lo único nuevo es el pool y sus adaptadores: `UnidadDeTrabajoJdbc`, `PedidosJdbc`, `InventarioJdbc`, `CatalogoJdbc`, `PeticionesJdbc`, `BandejaJdbc`, un cliente HTTP (`NotificadorHttp`), un servidor de carga con hilos virtuales y la raíz de composición `TiendaConPool`. El DDL es el del módulo hexagonal (sesión 07). Las cifras son de una corrida concreta (16 de noviembre de 2026; `resultados/` se borra y se regenera en cada corrida de `medir.sh`): el tamaño del pool sale de la cuenta con la retención que se mida ese día, y sale 2 o 3 en una máquina tranquila (en una muy cargada salió 7 y 13; en otra corrida la cuenta dio 1,47 y el pool fue de 3, con 204 pedidos bien y 9 489 errores en el defecto). Lo que no cambia es la forma: sin errores con la cuenta, agotamiento con el defecto, cero errores con la bandeja. Corrida con k6, PostgreSQL 17, JDK 21 y HikariCP 7.1.0.

Por qué no se reutilizó el banco de la lección: el enunciado pide el servicio de pedidos del módulo hexagonal. El banco queda como ejemplo de la lección.

## Paso 1 y 2: dónde está el pool

`UnidadDeTrabajoJdbc` es el único archivo con `getConnection(`. Los cinco repositorios llaman a `conConexion`, que da la conexión de la transacción en curso. Además de la conexión, la transacción recuerda las versiones de las filas que leyó: es lo que permite el control optimista (`update ... where version = ?`) sin que el dominio sepa nada. Salvedad: fuera de una unidad de trabajo no hay versiones que recordar, así que un `guardar` suelto actualiza sin `and version = ?`; los casos de uso siempre guardan dentro de `ejecutar`. Si el `rollback` falla (conexión rota), el error original se conserva y el del `rollback` queda como suprimido (`UnidadDeTrabajoJdbcTest`).

## Paso 3 y 4: pruebas

- Conexiones falsas (`UnidadDeTrabajoJdbcTest`, 6 pruebas): confirma si todo va bien, revierte si algo falla, devuelve la conexión también ante un `Error`, la anidada no pide otra, y una anidada que falla revierte una sola vez.
- PostgreSQL real (`ConPostgresTest`): `ContratoDelRepositorioDePedidos` y `ContratoDelInventario`, los del módulo hexagonal, sin cambios. Pasaron a la primera, incluido el conflicto de versión entre dos transacciones y la fecha al microsegundo.
- `CrearPedidoConJdbcTest`: "crear pedido" completo con los adaptadores JDBC. Reserva y guarda, repetir la clave devuelve el mismo pedido sin reservar otra vez, si el publicador falla se revierte todo (existencias incluidas), sin existencias no queda nada, y la entrega fallida deja los eventos en la bandeja y salen cuando el servicio vuelve.

## Paso 5: cuánto retiene "crear pedido" y qué pool sale

Con 100 pedidos/s y un pool de 10 (sin servicio lento): la retención media del pool fue de **2,36 ms** (máximo 17 ms). La cuenta de la sesión 02:

    conexiones = pedidos por segundo x segundos de retención = 500 x 0,00236 = 1,18

Con un margen del 50 % y redondeando hacia arriba: **pool de 2**. Comprobado con carga real: con el pool de 2 y 500 pedidos/s durante 20 s, 10 001 pedidos y 0 errores; el máximo de conexiones activas fue 1, la espera media 0,03 ms y p95 1,9 ms. El pool de 2 está lejos del límite de la base (30), así que quedan 28 conexiones para otras instancias. La cuenta es un promedio: el máximo simultáneo se acerca a 1 porque cada pedido dura 2 ms y llegan cada 2 ms; el margen cubre las rachas.

## Paso 6: el defecto, un servicio de 300 ms dentro de la unidad de trabajo

Con el pool de 2, `connectionTimeout` de 1 s y los eventos publicados directo (`NotificadorHttp` dentro de `ServicioDePedidos.crear`):

| | pedidos bien | errores | p95 | activas máx | en espera máx | uso medio / máx | espera media | timeouts |
|---|---|---|---|---|---|---|---|---|
| paso 5b (sin servicio lento) | 10 001 | 0 | 1,9 ms | 1 | 0 | 2 / 17 ms | 0,03 ms | 0 |
| paso 6 (defecto) | 136 | 9 560 | 1 001 ms | 2 | 501 | 310 / 341 ms | 1 000 ms | 9 560 |

Capacidad teórica del defecto: 2 conexiones / 0,3 s = 6,7 pedidos/s. En 20 s eso son unos 133, y se atendieron 136. El resto esperó 1 s y recibió 503. Las métricas del pool lo muestran sin mirar el código: el uso (usage) pasó de 2 ms a 310 ms, la espera (acquire) se fue al `connectionTimeout`, hubo 501 hilos esperando y todos los timeouts del pool.

## Paso 7: el aviso de fuga

Con el servicio a 2,5 s y `leakDetectionThreshold` de 2000 ms (HikariCP desactiva, con un aviso, los umbrales menores de 2000 ms; comprobado con la 7.1.0), sale `Connection leak detection triggered ... Apparent connection leak detected`. La traza (`resultados/aviso-de-fuga.txt`):

    at com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:99)
    at tienda.adaptadores.salida.jdbc.UnidadDeTrabajoJdbc.ejecutar(UnidadDeTrabajoJdbc.java:39)
    at tienda.aplicacion.ServicioDePedidos.lambda$crear$2(ServicioDePedidos.java:56)
    at tienda.aplicacion.ServicioDePedidos.crear(ServicioDePedidos.java:56)

La primera línea propia es siempre `UnidadDeTrabajoJdbc.ejecutar`: la traza es la de **quien pidió la conexión**, y en esta arquitectura quien la pide es siempre la unidad de trabajo, para cualquier caso de uso. Esa línea no distingue nada. La útil es la siguiente, `ServicioDePedidos.crear`: dice **qué caso de uso** la tiene retenida. Lo que dice sobre dónde buscar: no en el pool ni en el adaptador, sino en lo que ese caso de uso hace mientras tiene la conexión. El aviso no dice cuál es la llamada lenta; hay que leer el caso de uso.

## Paso 8: el arreglo, la bandeja de salida

Mismo pool (2), mismo `connectionTimeout` (1 s). El caso de uso publica con `BandejaJdbc`, que es un `insert` dentro de la misma transacción; un mensajero lee un lote, lo entrega a hilos virtuales sin conexión en la mano y solo borra los eventos que el servicio recibió con éxito.

| | pedidos bien | errores | p95 | activas máx | en espera máx | uso medio / máx |
|---|---|---|---|---|---|---|
| paso 6 (defecto) | 136 | 9 560 | 1 001 ms | 2 | 501 | 310 / 341 ms |
| paso 8 (bandeja) | 10 001 | 0 | 105 ms | 2 | 59 | 2,4 / 32 ms |

La retención vuelve de 310 ms a 2,4 ms y los errores de 9 560 a 0, sin tocar el tamaño ni la espera. El p95 de 105 ms (frente a 1,9 ms del paso 5b) tiene una causa: el mensajero también usa el pool de 2 (lee un lote y borra lo entregado cada 100 ms), y a 500 pedidos/s esas transacciones más largas hacen cola brevemente (59 en espera como máximo). Con la bandeja, el pool de 2 ya no es holgado: es la prueba de que la bandeja es también una decisión de capacidad, porque añade otro consumidor de conexiones. No se probó otro tamaño.

"Nada se perdió" se comprueba contra el servicio externo: recibió 10 001 llamadas para 10 001 pedidos creados, el mensajero contó 10 001 y la bandeja quedó vacía. Se corrigió además un defecto de la primera versión del mensajero: ignoraba si la entrega había funcionado y borraba el lote entero. Ahora recoge el resultado de cada entrega y borra solo lo entregado; `CrearPedidoConJdbcTest.siLaEntregaFallaElEventoSeQuedaEnLaBandejaYSaleCuandoElServicioVuelve` lo prueba con un servicio que responde 503: los tres eventos siguen en la bandeja y, al volver, salen los tres (el servicio los recibió una vez cada uno). La garantía es "al menos una vez": si el servicio recibe el evento y el mensajero cae antes de borrarlo, se reenvía; por eso el id del evento es estable. `siElMensajeroCaeEntreEntregarYBorrarSeReenviaConElMismoId` lo prueba: la entrega recibe los tres, lanza una excepción antes de borrar, los tres siguen pendientes; en la segunda vuelta el servicio acumula 6 llamadas con solo 3 ids distintos.

## Paso 9: la unidad anidada con el pool en 1

`ConPostgresTest.conElPoolEnUnoUnaUnidadAnidadaQueSePideOtraConexionSeBloquea`: con una unidad de trabajo que pide siempre su conexión (`UnidadDeTrabajoQueSiemprePideOtra`, solo en pruebas), la anidada espera el `connectionTimeout` entero (500 ms) y falla con `Connection is not available, request timed out after ...`: la externa no suelta la suya hasta que la interna termine, y la interna espera una que nadie va a devolver (un interbloqueo que el timeout convierte en error). Con `UnidadDeTrabajoJdbc`, que se une a la transacción abierta, el mismo caso con el pool en 1 funciona y devuelve `listo`.

## Paso 10: la regla, en una línea

**El tamaño del pool sale de las peticiones por segundo por el tiempo que cada caso de uso tiene la conexión, y ese tiempo debe ser el de la base: nada lento (HTTP, colas, esperas) puede ocurrir mientras se tiene una.**

## Criterios

| Criterio | Resultado |
|---|---|
| Solo el adaptador de transacciones toca el pool | `ArquitecturaDelPoolTest`: `getConnection(` solo en `UnidadDeTrabajoJdbc.java` |
| El dominio y la aplicación no cambiaron | se compilan desde el módulo hexagonal; ningún archivo propio en esos paquetes; huellas en `resultados/` |
| El adaptador devuelve la conexión en todos los caminos | `UnidadDeTrabajoJdbcTest` |
| El agotamiento se reproduce y se corrige sin cambiar el tamaño | pasos 6 y 8, pool de 2 en ambos |
| El tamaño sale de una cuenta comprobada con carga | paso 5: cuenta 1,18, pool de 2, 500/s con 0 errores |
