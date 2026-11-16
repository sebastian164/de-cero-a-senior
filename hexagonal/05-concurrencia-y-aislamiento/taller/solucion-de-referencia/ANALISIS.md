# Taller 05 — Concurrencia en los pedidos

Proyecto en esta carpeta. Compilado con `javac -Xlint:all -Werror` y ejecutado con JDK 21.0.13: **108 comprobaciones,
0 fallos**; `DiezVeces` (paso 7) 40 comprobaciones, 0 fallos. El dominio es idéntico al de las sesiones 02 y 04
(compáralo con `diff -r`).

## Paso 1. La prueba determinista, con el adaptador de la sesión 04

`paso1/Paso1.java` deja un monitor, lee las existencias para el pedido de Ana y, justo después, deja que otro hilo cree el
pedido de Luis por el mismo monitor. Se compila contra cada solución (ver el README de esta carpeta):

```
== con 04-transacciones
luis: PENDIENTE
ana:  PENDIENTE
vendidas=2 quedan=0 vendidas+quedan=2 (iniciales 1)
== con 05-concurrencia-y-aislamiento
luis: PENDIENTE
ana:  rechazada (ReglaDeNegocioViolada: no hay existencias suficientes de monitor)
vendidas=1 quedan=0 vendidas+quedan=1 (iniciales 1)
```

Con el adaptador de la 04 las dos transacciones son atómicas, las dos confirman y el monitor se vende dos veces: la
actualización perdida. Las existencias dicen 0 (no -1), porque cada una guardó "1 - 1" calculado sobre la misma lectura.

## Paso 2. Qué se disputa

Las **existencias de cada producto**: varios pedidos distintos las leen y las escriben. El pedido también lleva versión,
porque confirmar y cancelar el mismo pedido a la vez es otra disputa (cancelar devuelve unidades: un pedido cancelado y
confirmado a la vez acabaría confirmado con sus unidades devueltas).

Decisión: **la versión no va en el dominio**. La lleva el almacén: cada transacción anota la versión de cada fila que lee
(`leidas`) y, al guardar y al confirmar, exige que la confirmada siga siendo esa. Así el dominio no cambió ni una línea, y
es exactamente lo que hace JPA con `@Version` en una entidad gestionada (sesión 07). El precio: un adaptador que escriba
sin haber leído antes en la misma transacción no detecta nada; el caso de uso siempre lee antes de escribir.

Nota: el banco del curso pone `version` en `Cuenta` (en el dominio). Aquí va en el adaptador, porque el criterio de aceptación
exige que el dominio no cambie para soportar concurrencia. El coste es que hay que leer y escribir en la misma transacción.

## Paso 3. Control de versión y excepción

`AlmacenEnMemoria.exigirVersionVigente` y `ConflictoDeConcurrencia` en `tienda.aplicacion`. También detecta dos
transacciones simultáneas con la misma clave de idempotencia (la que confirma segunda recibe un conflicto).

## Paso 4. Reintento

`ServicioDePedidos.conReintento`, `INTENTOS = 3`, alrededor de cada unidad de trabajo (`crear`, `confirmar`, `cancelar`). Se
repite el caso de uso entero: se vuelve a leer y el dominio vuelve a decidir. Al agotarse, quien llamó recibe
`ConflictoDeConcurrencia` (la sesión 06 lo traduce a 409: puede reintentar con la misma clave). Sin espera entre intentos,
como en el banco; en producción llevaría espera con azar.

## Paso 5. El doble que provoca el conflicto

`InventarioConInterferencia` deja actuar a otro hilo justo después de leer las existencias.

```
  ok    el caso de uso termina bien al segundo intento
  ok    necesitó dos lecturas
  ok    no se perdió ninguna venta: 10 - 3 - 2
  ok    tras 3 intentos, el conflicto llega a quien llamó
  ok    intentó tres veces
  ok    al rendirse no dejó existencias descontadas: solo las de los otros tres
  ok    ni pedido
  ok    y la misma clave se puede reintentar
```

Además, `laMismaClaveALaVez`: ocho hilos envían la misma clave a la vez; resultado, un solo pedido, una sola unidad
descontada, un `nuevo = true` y ningún error que no sea conflicto.

## Paso 6. Varios hilos: lo que debe ser cierto siempre

Definido antes de ejecutar: 8 hilos × 40 pedidos de 1 a 3 teclados, con 300 en existencias.

1. Las existencias nunca se ven negativas.
2. Unidades vendidas (sumadas desde los pedidos guardados) + las que quedan = 300.
3. Cada pedido creado está guardado.
4. Cada intento termina creado, rechazado por existencias o rendido por conflicto; ningún otro error.

Ninguna afirmación depende de cuántos se crean o se rinden.

## Paso 7. Diez veces seguidas

`java -cp salida tienda.DiezVeces`. Pedidos rendidos por conflicto en cada ejecución, en dos corridas de esta solución:

```
rendidos por ejecución: [3, 0, 0, 0, 0, 0, 2, 0, 0, 0]     (existencias 100)
rendidos por ejecución: [0, 0, 0, 3, 2, 0, 1, 0, 0, 0]     (existencias 300)
```

Las cuatro afirmaciones pasaron las diez veces en las dos corridas. El número de rendidos cambia en cada ejecución.

## Paso 8. Bloqueo pesimista con dos productos

Bloquearía las filas de existencias **en orden de producto** (`SELECT … WHERE producto IN (…) ORDER BY producto FOR UPDATE`).
Sin orden: el pedido A (teclado y monitor) bloquea teclado; el B (monitor y teclado) bloquea monitor; A espera monitor y B
espera teclado. Ninguno avanza: interbloqueo. PostgreSQL lo detecta y cancela uno (`40P01`); con `ORDER BY producto FOR UPDATE` los dos piden los bloqueos en el mismo orden y solo hay espera. Puedes reproducirlo con dos sesiones de `psql`.

## Paso 9. "Un cliente no puede tener más de tres pedidos pendientes"

El control de versión **no** lo evita. Las dos peticiones leen los tres pendientes del cliente, cada una decide que puede y
cada una **inserta un pedido nuevo**: no modifican ninguna fila que la otra haya leído, así que ninguna versión cambia. Es un
sesgo de escritura (la regla cruza varias filas y cada transacción escribe en una distinta).

Propuesta: convertir la regla en una fila que se disputa. Un agregado `PedidosPendientesDeCliente` (cliente, cuántos) con su
versión: crear lo incrementa y valida el máximo en el dominio, confirmar y cancelar lo decrementan; dos creaciones
simultáneas escriben la misma fila y la segunda recibe conflicto y reintenta (y ya ve 4). Alternativas en base de datos:
`SERIALIZABLE` con reintento (PostgreSQL cancela una con `40001`), o un
`SELECT … FOR UPDATE` sobre la fila del cliente antes de contar.

## Criterios de aceptación

| Criterio | Resultado |
|---|---|
| Dos pedidos simultáneos no venden la misma unidad | `conVersion` y `Paso1` contra la 05: una sola venta |
| El caso de uso reintenta y tiene un límite | `elCasoDeUsoReintenta`, `elCasoDeUsoSeRinde` |
| La prueba con hilos pasa diez veces seguidas | `DiezVeces`: 40 comprobaciones, 0 fallos, en cada ejecución |
| Las afirmaciones no dependen del orden de ejecución | Son invariantes (paso 6), no conteos |
| El dominio no cambió para soportar concurrencia | `diff -r` del dominio 04 contra 05: sin diferencias |
