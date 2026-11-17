# Taller 06 — Redis más allá de la caché

Código: `src/main/java/nosql/` y `src/test/java/nosql/ProteccionTest.java` (más `ProcesoDeCobro`, el proceso que se mata con `kill -9`). Lo medido queda en `salida/resultados.txt` al ejecutar las pruebas. El borde HTTP es `com.sun.net.httpserver` (sin Spring); el servicio de pedidos es el del módulo hexagonal con los adaptadores de MongoDB del taller 04.

## Capas

| Capa | Qué hay | Conoce |
|---|---|---|
| Aplicación (`nosql.aplicacion`) | Puertos de salida `PasarelaDePagos`, `RegistroDeIdempotencia`, `LimitadorDeTasa`, `CerrojoDeTareas`, `RegistroDeTurnos`, `PedidosAbandonados`, `HistorialDePedidos`; puerto de entrada `ListarPedidosPaginados`; casos de uso `CrearPedidoConCobro` (decora `CrearPedido`), `CancelarAbandonados`, `ServicioDeHistorial` | Solo puertos y el dominio del núcleo |
| Adaptadores de Redis (`nosql.adaptadores.redis`) | `IdempotenciaRedis`, `LimiteDeTasaRedis` (Lua), `CerrojoRedis` | Lettuce |
| Adaptadores de MongoDB (`nosql.adaptadores.mongo`) | `PasarelaMongo` (el servicio de pagos simulado), `TurnosMongo`, `AbandonadosMongo`, `HistorialMongo` | El controlador de Java |
| Adaptador HTTP (`nosql.adaptadores.http`) | `AdaptadorHttp`: interpreta HTTP y JSON, aplica límite e idempotencia del borde **a través de puertos** y traduce errores | `com.sun.net.httpserver`, `org.bson` (para el JSON) |
| Composición (`nosql.proteccion.Composicion`) | Une todo | Todo |

`tecnologiaSoloEnAdaptadoresYComposicion` recorre los módulos 04 a 06 y el núcleo (79 archivos): `io.lettuce`, `com.mongodb`, `org.bson` y `com.sun.net.httpserver` no aparecen fuera de `adaptadores/` y de la composición. `AdaptadorHttp` no devuelve el texto de ninguna excepción: un fallo del almacén es un 503 genérico, un error inesperado un 500 genérico, un conflicto agotado un 409 `conflicto, reintente`, una regla del negocio un 422 con su mensaje (las del dominio) y un cuerpo mal formado un 400. Una `MongoException` nunca llega al adaptador HTTP: la traduce el adaptador de MongoDB.

## Paso 1. Idempotencia del borde

Diez peticiones idénticas a la vez con `Idempotency-Key`, diez repeticiones con claves distintas: **cada repetición {201: 1, 409: 9}**, un pedido creado, un cobro, un id. La misma clave con otro cuerpo: **422**. La misma clave y el mismo cuerpo después de terminar: se repite la respuesta 201 guardada. La clave guarda `EN_CURSO|huella` (caduca en 30 s en esta prueba) y, al terminar, `HECHO|huella|estado|cuerpo` 24 h.

Hay **dos idempotencias separadas**: la del borde (Redis, evita trabajo y da respuestas repetidas) y la de la transacción (`PeticionesProcesadas` del núcleo: la clave es el `_id` de la colección `peticiones`, en la misma transacción que el pedido), que es la que de verdad impide el duplicado si Redis olvida.

## Paso 2. El hueco entre cobrar y apuntar

`ProcesoDeCobro` es un proceso Java aparte con el servicio completo y su servidor HTTP; se pide a sí mismo un pedido por HTTP y se detiene (60 s) **después de cobrar y antes de guardar la respuesta en Redis**. La prueba lo mata con `kill -9` (`Process.destroyForcibly`) en cuanto imprime `COBRADO`, espera a que venza el `EN_CURSO` (2 s) y lanza el mismo flujo con la misma clave:

| Pasarela de pagos | Pedidos | Cobros |
|---|---|---|
| Ingenua (cada llamada cobra) | 1 | **2** (doble cobro) |
| Idempotente (la clave del cliente es la clave del cobro) | 1 | **1** |

El reintento responde 200 (el pedido ya existía) y las existencias se descuentan una sola vez. El orden es *pedido y después cobro* (`CrearPedidoConCobro`): si el proceso muere entre ambos, el reintento encuentra el pedido, lo devuelve y cobra. Si el **cliente no reintenta**, el pedido queda sin cobrar; el verificador del taller 07 lo señala (`pedido sin cobro`), y en producción lo cubre un trabajo de conciliación. La única defensa contra el doble cobro es que el servicio externo reciba la misma clave.

## Paso 3. Límite de tasa

Ventana deslizante con registro en un `ZSET` y un script de Lua (usa `TIME` de Redis, no el reloj de las instancias). Se prefirió a una ventana fija porque esta deja pasar el doble en el borde, y a un cubo de fichas porque se pidió un límite estricto de "10 en cualquier minuto". Prueba con límite 10 por W = 3 s: 5 en t=0, 5 en 0,6 W (todas pasan), a 0,9 W rechazada con reintento en ~270 ms, a 1,05 W pasan **5 de 8**. En los 15 instantes aceptados, ninguna ventana de 2.900 ms contiene más de 10. Por HTTP: un cliente que envía 100 pedidos seguidos con límite 10 por minuto obtiene 10 × 201 y 90 × `429` con `Retry-After: 60`; otro cliente recibe 201.

## Paso 4. Sin Redis: falla abierto

Decisión: **ABIERTO** para el límite. El límite protege de abusos, no de la corrupción: los invariantes (existencias, no duplicar pedidos) los garantiza MongoDB; cerrar el límite convertiría una caída de Redis en una caída de las ventas. Con Redis detenido, 10 peticiones con la misma clave dan 10 respuestas 2xx, **un** pedido, **un** cobro (operaciones de Redis degradadas contadas: 20). La alternativa CERRADO se implementó también: responde 503 y el servicio deja de vender mientras Redis esté caído. Para otro caso (un límite que protege a un tercero con coste por llamada) la respuesta sería la contraria.

## Paso 5. Cerrojo con ficha y turno

`CerrojoRedis.adquirir`: `SET NX PX` en Redis da la exclusión; **el turno lo emite MongoDB** (`TurnosMongo.siguiente`: `$inc` sobre un documento de la colección `turnos`), después de tomar el cerrojo, y se comprueba que el cerrojo sigue siendo del que pide antes de devolver la concesión. Lo que se escribe lleva el turno: `CancelarAbandonados` abre una unidad de trabajo por pedido y lo primero que hace es `TurnosMongo.exigir`, un `upsert` del documento `<tarea>:aplicado` con filtro `turno <= mi turno`; si ya hay uno mayor el upsert choca con el `_id` y se lanza `TurnoCaducado` antes de tocar nada.

Tres instancias a la vez: una obtiene el cerrojo (turno 1). La dueña se para 1.200 ms con una vida de 800 ms; B lo toma con el turno 2 y cancela los dos pedidos abandonados. Al despertar, con otro abandonado nuevo en la lista, A intenta cancelar con el turno 1: **rechazada** (`TurnoCaducado`), el pedido sigue `PENDIENTE` y sus existencias reservadas. La ficha de A tampoco libera el cerrojo de B. Control: la misma escritura sin comprobar el turno se aplicaría.

**Qué pasa si Redis olvida el contador** (reproducido: `paso5b`). La versión anterior emitía el turno con `INCR` en Redis y MongoDB solo guardaba el mayor visto: tras un `FLUSHALL` el contador volvía a 1 y el dueño legítimo era rechazado como caducado hasta que alcanzara al de MongoDB, y la tarea quedaba parada en silencio. Ahora el contador vive en MongoDB: tras cuatro turnos y un `FLUSHALL`, el siguiente dueño recibe el turno 6 (> 5) y cancela; un dueño anterior que siga vivo es rechazado aunque el cerrojo ya no exista.

**Las cancelaciones de la tarea son cancelaciones del hexágono**: `CancelarAbandonados` usa `Pedido.cancelar()` y `Reserva.liberar` del dominio, la misma `UnidadDeTrabajo` del núcleo y deja el evento `PedidoCancelado` en la bandeja con el mismo id que usa `ServicioDePedidos.cancelar` (`pedido-cancelado:<id>`). Comprobado: 2 cancelaciones de la tarea = 2 eventos, y el verificador del 07 exige un evento por transición.

## Paso 6. ¿Hacía falta el cerrojo?

Con el filtro `estado: "PENDIENTE"`, dos instancias a la vez cancelan cada pedido exactamente una vez (60 de 60, ninguno dos veces). Pero si la **devolución de existencias** va aparte, se hace por cada intento: medido, existencias 1.120 en vez de 1.000 (120 unidades de más). Con `CancelarAbandonados` (devolución en la misma transacción que la cancelación): cancelados 60, existencias 1.000, un evento por cancelación; las dos instancias chocan entre sí (todas escriben el mismo documento `aplicado`, el precio del turno: serializa las cancelaciones; 60 choques en 120 intentos) y lo resuelve la pasada siguiente. Así que el filtro hace la cancelación correcta; el cerrojo evita trabajo repetido (las dos instancias recorriendo la misma lista y chocando) y los efectos que no son idempotentes; el turno protege la escritura aunque el cerrojo caduque.

## Paso 7. Desalojo de claves de idempotencia

Con `maxmemory 5mb` y `allkeys-lru`, se guardan 3.000 claves de idempotencia y luego se llena la caché de 60.000 productos: **sobreviven 0 de las 3.000**. Esas peticiones podrían ejecutarse dos veces. Configuración correcta: dos instancias de Redis, una para la caché (`allkeys-lru`, datos prescindibles) y otra para el estado (idempotencia, límites, cerrojos) con `noeviction`: probado con la segunda a 5 mb, guarda ~6.500 claves y luego responde `OOM command not allowed when used memory > 'maxmemory'`, sin perder ninguna. Fallar a gritos es mejor que olvidar en silencio.

**Un `OOM` que no debería ocurrir.** En una ejecución anterior esta prueba falló una vez con `OOM command not allowed` *con `allkeys-lru` activo*, con la máquina muy cargada. No se ha podido reproducir ni se conoce la causa: se descartó que sea vaciar de forma diferida y bajar `maxmemory` de inmediato (87 MB vaciados de forma asíncrona y `maxmemory 5mb` al instante, 0 errores). Lo que sí se hizo: vaciar Redis de forma síncrona y esperar a que la memoria baje antes de cada prueba; reintentar la escritura si llega un `OOM` transitorio, contarlos (`salida/resultados.txt`: "OOM transitorios...") y guardar `INFO memory`/`INFO stats` en `salida/redis-oom-paso7.txt` la primera vez que ocurre; y exigir 0 errores de Redis en el llenado del paso 6 del taller 05. Lección operativa: `allkeys-lru` no garantiza que las escrituras no fallen.

## Qué pasa si la clave desaparece

| Uso | Si la clave desaparece |
|---|---|
| Idempotencia del borde | Se pierde la respuesta guardada; la repetición vuelve a ejecutar, pero la clave de la transacción devuelve el mismo pedido y la pasarela deduplica por clave: no hay duplicado |
| Límite de tasa | Se reinicia la ventana: durante una ventana se puede pasar el doble de lo pactado |
| Cerrojo | Dos instancias creen tener el cerrojo; el turno (en MongoDB) rechaza al más antiguo |
| Turno | **No vive en Redis**: lo emite y lo comprueba MongoDB. Un `FLUSHALL` no lo toca (`paso5b`) |
| Caché del catálogo | Se recarga de MongoDB; la carga única evita la estampida |

## Criterios de aceptación

| Criterio | Dónde |
|---|---|
| Diez peticiones idénticas crean un pedido y cobran una vez | `paso1`, diez repeticiones |
| El hueco entre cobrar y apuntar está demostrado y cerrado | `paso2` |
| El límite nunca deja pasar más de lo pactado, ni en el borde | `paso3` |
| Qué pasa sin Redis está decidido, implementado y escrito | `paso4` y la sección de arriba |
| Un dueño caducado no puede escribir | `paso5`, y `paso5b` para el caso de que Redis olvide |
| `ANALISIS.md` responde "¿qué pasa si la clave desaparece?" | La tabla de arriba |
