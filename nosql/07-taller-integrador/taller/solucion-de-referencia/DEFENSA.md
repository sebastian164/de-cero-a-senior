# Defensa — servicio de pedidos sobre MongoDB y Redis

Para quien vaya a operarlo. Todo lo que se afirma aquí está medido en el simulacro (`salida/simulacro.txt`, que genera la prueba) o en una prueba concreta, que se cita.

## 1. El modelo

**Consultas y volúmenes a tres años** (50.000 clientes, 5.000 productos, 1.000.000 de pedidos, un cliente muy activo con 20.000):

| # | Consulta o escritura | Frecuencia | Cómo se resuelve |
|---|---|---|---|
| C1 | Crear pedido (varias líneas, descuenta existencias, clave de idempotencia, evento) | 10/s en pico | Una transacción: `pedidos` + `productos` + `peticiones` + `bandeja` |
| C2 | Pedido por id | 50/s | `_id`; un documento |
| C3 | Pedidos de un cliente, paginados por clave | 30/s | Índice `{clienteId, fecha:-1, _id:-1}`; cada página examina lo que devuelve |
| C4 | PENDIENTE más viejos que el umbral (tarea, cada 0,5 s en el simulacro; cada minuto en producción) | 2/s | Índice `{estado, fecha}` |
| C5 | Cambiar de estado (aprobar, confirmar, cancelar) | 10/s | Una transacción: estado + historial + versión + evento |
| C6 | Precio y existencias de un producto | 200/s | Caché de la vista en Redis; si no, `productos` por `_id` |
| C7 | Cambiar el precio | 1/min | `$set` del precio + evento, en una transacción |

**Documentos.** `pedidos`: `{_id, clienteId, estado, fecha, fechaMicros, total, lineas[{productoId, nombre, cantidad, precio}], historial[{estado, fecha}], version}`: 447 bytes de media (621 de máximo) con 1 a 5 líneas; 1.000.000 de pedidos son ~450 MB más los índices (~100 MB). `productos`: `{_id, nombre, precio, existencias, version}` (73 bytes). `peticiones`: `{_id: clave, pedidoId, fecha}`: una por pedido. `bandeja`: un evento por cambio de pedido (en torno a 2,5 por pedido): **crece sin parar** (ver límites). `turnos`: dos documentos por tarea. `cobros` vive en la base del servicio de pagos, no en esta. **Ningún arreglo sin tope:** `lineas` ≤ 50 (decorador `CrearPedidoAcotado` en la aplicación y `maxItems` en el validador de la colección) e `historial` ≤ 20 (`$slice` al escribir y `maxItems` en el validador). Un `POST /pedidos` con 51 líneas responde 422.

## 2. Tres decisiones discutibles

1. **Incrustar líneas e historial en el pedido; referenciar al cliente.** La alternativa descartada es la contraria: pedidos dentro del cliente. El cliente activo (20.000 pedidos, unos 18 al día) llegaría al límite de 16 MiB hacia los 37.000 pedidos (medido en el taller 01: falló con el código 10334) y cada `$push` reescribiría un documento que crece. Las líneas, en cambio, se leen siempre con el pedido y tienen tope. Se copian el nombre y el precio del producto en la línea: el pedido es un hecho y no debe cambiar si el catálogo cambia (comprobado: se cambia el precio y la línea sigue igual).
2. **Transacción en toda escritura del pedido, sin transacción en lo demás.** Cada escritura de pedido (crear, aprobar, confirmar, cancelar, la cancelación de la tarea) escribe además su evento en la bandeja **en la misma transacción**: es lo que garantiza que ningún cambio ocurra sin su evento (el verificador lo exige tras cada prueba). Sin transacción: las lecturas, el contador de turnos (un contador), sembrar productos y el cobro (es otro servicio, con su propia base). Lo que se paga: un conflicto entre dos transacciones que tocan el mismo producto se resuelve reintentando el caso de uso entero (política: espera creciente con azar, 50 intentos, tope 1 s; en el taller 04, con la política original de 3 intentos, 195 de 200 compradores se iban con un 409 que no merecían). La alternativa que sería más barata y que el puerto `Inventario` del núcleo no permite sin cambiarlo es descontar con `$inc` condicional, sin leer ni transacción para el stock.
3. **Qué se cachea: la vista de producto (precio y existencias), no los pedidos.** Con una caducidad de 4 s con ±20 % de azar, invalidación al cambiar el precio por el servicio y carga única por instancia. Los pedidos no se cachean: se escriben y se leen para decidir, y una caché desfasada decidiría mal (el taller 05 lo reproduce: la base dice `CONFIRMADO` y la caché `PENDIENTE`). **El precio con el que se crea un pedido sale siempre de MongoDB**, nunca de la caché; la caché es solo para mostrar.

## 3. Qué pasa si se pierde Redis, uso por uso

| Uso | Efecto | Medido |
|---|---|---|
| Caché del catálogo | Cada petición va a MongoDB (más lenta); al volver Redis, la caché se recupera sola | Redis detenido 120 s: ~4.200 a 4.500 peticiones, **0 errores 5xx**; la caché vuelve a dar aciertos ~1,1 s después de volver Redis (con la reconexión de Lettuce acotada a 500 ms; con la de por defecto fueron entre 4 y 19 s) |
| `FLUSHALL` | Sin estampida: carga única por instancia | 24 a 43 lecturas a MongoDB para 60 claves posibles (de 8.000 a 27.000 peticiones, según la ejecución) |
| Idempotencia del borde | Se degrada: se pierde la respuesta guardada y la protección contra la doble ejecución en vuelo; la idempotencia de la transacción (clave única en `peticiones`) sigue impidiendo el pedido duplicado y la pasarela deduplica por clave | Con Redis detenido, 10 peticiones iguales dan 1 pedido y 1 cobro (taller 06) |
| Límite de tasa | **Falla abierto**: se deja pasar. El límite protege de abusos, no de la corrupción; cerrarlo convertiría una caída de Redis en una caída de las ventas. Para un tercero con coste por llamada sería al revés | Taller 06, paso 4 |
| Cerrojo de la tarea | La tarea **no corre** mientras Redis no esté (no se puede tomar el cerrojo). No es grave: los abandonados se cancelan más tarde. El turno vive en MongoDB, no se pierde | `paso5b` del taller 06 |

## 4. Qué garantiza y qué no

**Garantiza**
* **Precio viejo, como mucho 4,8 s** si alguien cambia el precio directamente en MongoDB (TTL 4 s + 20 % de azar): medido 4,6 a 4,7 s para 100 productos. Si el cambio pasa por el servicio, la invalidación tarda milisegundos (el publicador sondea cada 200 ms). Las existencias que muestra la caché pueden estar viejas lo mismo; **no se decide con ellas**.
* **No se vende lo que no hay y no se vende dos veces la última unidad**: 20 compradores por 5 unidades, 10 veces, dan exactamente 5 pedidos y existencias 0.
* **Nada a medias**: ningún pedido sin su descuento, su clave de idempotencia y su evento, ni al revés, ni con `docker kill` en plena carga (el verificador tras la prueba).
* **Primario de MongoDB que cae**, con `w: "majority"` (todas las transacciones lo usan): lo confirmado sobrevive (taller 03, paso 6: con `w: 1` la escritura confirmada se pierde; con `majority`, no). Durante la elección de un nuevo primario (varios segundos) las escrituras fallan con 503 genérico y se reintentan; las lecturas de pedidos también, porque se leen del primario.

**No garantiza (límites reales)**
* **Una instancia muerta a mitad de una transacción bloquea los productos que tocaba hasta que MongoDB aborta la transacción**: ~1,5 veces `transactionLifetimeLimitSeconds`. Medido con `kill -9` de un proceso: 7,5 s con 5 s, 22 s con 15 s, **90 s con el valor por defecto de 60 s** (`TransaccionHuerfanaTest`). El compose fija 15 s: las transacciones del servicio duran milisegundos, así que 15 s es 100 veces más de lo que necesitan, y bajarlo más abortaría transacciones legítimas lentas (una pausa de GC, una red lenta). Mientras dura, los pedidos que tocan esos productos reciben 409 o 503 y el cliente reintenta con la misma clave; en el simulacro, `docker kill` de una instancia con 60 pedidos en vuelo: todos los reintentos entraron en 2 a 20 s; y con `docker pause` del dueño de la tarea, el relevo tardó de 3 a 17 s.
* **Un pedido sin cobrar si el cliente no reintenta.** El orden es crear el pedido y cobrar. Si la instancia muere entre ambas cosas, el reintento con la misma clave cobra (la pasarela es idempotente), pero si nadie reintenta el pedido queda sin cobro. El verificador lo detecta (`pedido sin cobro`); en producción hace falta un trabajo de conciliación (pedidos sin cobro con más de N minutos).
* **Tras volver Redis, una instancia tarda ~1,1 s en reconectar**; durante ese tiempo no tiene caché, ni límite de tasa, ni idempotencia del borde (abierto): una ráfaga en esa ventana puede pasar sin límite y se protege solo con la idempotencia de la transacción.
* **La tarea de cancelación serializa las cancelaciones**: todas escriben el documento `turnos` (es el precio de que el turno proteja la escritura). Con dos instancias que cancelan a la vez, la mitad de los intentos chocan y se reintentan en la pasada siguiente. Con cerrojo solo una instancia cancela y no hay choques. El contador de cancelaciones de una instancia no cuenta el lote que se abortó a medias por turno caducado (la base sí lo sabe todo).
* **La bandeja no se purga**: los eventos publicados se quedan. Hay que añadir una caducidad (índice TTL sobre la fecha de publicación) o un archivado; no está hecho.
* **No hay envío**: el servicio del módulo hexagonal no tiene el estado `ENVIADO`; "pagar" es `confirmar`.
* **Contención sobre un producto muy vendido**: la política de reintento convierte el rechazo en espera (hasta ~2 s en cola con 200 compradores sobre tres productos). Un producto estrella pediría descontar con `$inc` condicional.

## 5. Guía de guardia (métricas de `GET /metricas`, una por instancia)

| Métrica | Alerta | Primer paso |
|---|---|---|
| `cacheAciertos` / (`cacheAciertos` + `cacheFallos`) | Por debajo del 80 % cinco minutos | Mirar `erroresRedis`; si no crece, ¿se vació Redis? ¿cambió el patrón de lectura? |
| `erroresRedis`, `limiteRedisCaido`, `idempotenciaDegradada` | Crecen | Redis caído o lento: comprobarlo; el servicio sigue respondiendo (más lento y sin límite) |
| `reintentosPorConflicto` | Más de ~5 por pedido creado de media, sostenido | Hay un producto caliente; mirar qué producto concentra los pedidos |
| `conflictosAbandonados` | Cualquier valor sostenido > 0 | Clientes recibiendo 409 con stock disponible: subir intentos o rediseñar el descuento |
| `peticionesLimitadas` | Pico con muchos clientes distintos | Un cliente aislado es un abuso normal; muchos a la vez, mirar si el límite es bajo |
| `canceladosPorTarea` | Cero durante más de dos periodos cuando hay abandonados | ¿Quién tiene el cerrojo (`tieneCerrojo`)? ¿Redis está arriba? |
| `turnosRechazados` | Más de 1 de vez en cuando | Un dueño se pausó más que la vida del cerrojo: subir `VIDA_CERROJO_MS` o investigar la pausa |
| `eventosPendientes` | Crece cinco minutos seguidos | El publicador está parado: comprobar el documento `control` (`pausado`) y los registros |
| `conflictosDeLaTarea` | Alto y sostenido | Dos instancias cancelando a la vez: el cerrojo falla; revisar Redis |

## 6. Cuándo no usaría esto

Llevaría los pedidos a PostgreSQL si: (a) aparecen **invariantes entre varias entidades que cambian a la vez con frecuencia** (contabilidad, reservas con plazas que dependen de otras tablas): en MongoDB hay que hacer a mano lo que ahí es una restricción o una `UPDATE ... WHERE`; (b) el negocio pide **informes y consultas que no se conocen de antemano** (uniones, agregados por cualquier dimensión): el modelo de MongoDB está hecho para las siete consultas de arriba y cualquier otra paga un `COLLSCAN` o un índice nuevo; (c) la **contención sobre pocas filas calientes** pasa a ser el caso común (un producto estrella con miles de compradores a la vez): el descuento atómico `UPDATE stock SET n = n - 1 WHERE n >= 1` de una base relacional es más barato y más fácil de razonar que lo que hay aquí; (d) el equipo no quiere operar **dos bases de datos más** (MongoDB y Redis) y tiene un volumen que una sola PostgreSQL con réplicas aguanta con holgura. Con 1.000.000 de pedidos en tres años, que es poco, PostgreSQL lo aguantaría sin esfuerzo.
