# Taller 04 — Transacciones en los pedidos

Proyecto en esta carpeta. Compilado con `javac -Xlint:all -Werror` y ejecutado con JDK 21.0.13: **86 comprobaciones,
0 fallos**. El dominio es idéntico al de la sesión 02 (compáralo con `diff -r`).

## Paso 1. La prueba que provoca el fallo, sin unidad de trabajo

`PruebasDeTransacciones.sinTransaccion`: `PedidosQueFallan` envuelve el repositorio real y falla al guardar el pedido,
justo después de que el caso de uso guardó las existencias; `SinTransaccion` no agrupa nada. Salida:

```
Transacciones, sin unidad de trabajo (paso 1):
  ok    crear falla al guardar el pedido
  ok    pero las existencias ya se descontaron
  ok    y el cliente no tiene ningún pedido
  ok    al reintentar se descuenta otra vez: 4 teclados menos por un pedido de 2
  ok    y el último monitor ya no se puede vender a otro
```

Lo que queda mal: 2 teclados y 1 monitor descontados de un pedido que no existe; al reintentar se descuentan otra vez
(10 → 8 → 6 teclados para un pedido de 2), y el último monitor queda "vendido" a nadie: el siguiente cliente es rechazado.

## Paso 2. El puerto

`UnidadDeTrabajo.ejecutar(Supplier<T>)`: "todo lo que ocurra dentro se confirma junto, o no ocurre". El mismo nombre y forma
que en el banco, porque es la que mejor se lee desde el caso de uso.

## Paso 3. El adaptador

`AlmacenEnMemoria` implementa ahora `RepositorioDePedidos`, `Inventario`, `PeticionesProcesadas`, `PublicadorDeEventos` y
`UnidadDeTrabajo`: detrás hay una sola "base". Cada transacción tiene su cuaderno (`Transaccion`) en un `ThreadLocal`; las
lecturas ven primero lo propio pendiente y después lo confirmado; `confirmar` lo pasa todo de una vez; una excepción lo
descarta. Sustituye a `PedidosEnMemoria`, `InventarioEnMemoria` y `EventosEnMemoria` de la sesión 03.

## Paso 4. Con unidad de trabajo

```
Transacciones, con unidad de trabajo (paso 4):
  ok    crear falla al guardar el pedido
  ok    no queda ninguna existencia descontada
  ok    ni pedido
  ok    ni la clave anotada
```

## Paso 5. Casos de uso sin transacción

`porId` y `deCliente`: hacen una sola lectura y no escriben. No hay nada que agrupar; abrir una transacción solo retendría
recursos. `crear`, `confirmar` y `cancelar` sí van en una: escriben pedido, existencias, clave o evento, y todo eso debe ir
junto (en `cancelar`, devolver existencias y marcar el pedido; en `confirmar`, el estado y el evento que dispara el correo).

## Paso 6. Idempotencia

`Solicitud` lleva la clave; `PeticionesProcesadas` anota `clave → pedido` **dentro** de la unidad de trabajo, y la
respuesta es `Resultado(pedido, nuevo)`.

```
Idempotencia (paso 6):
  ok    la misma petición dos veces crea un solo pedido
  ok    y descuenta una sola vez
  ok    la segunda respuesta es de éxito, con el mismo pedido
  ok    y dice que no es nuevo
  ok    una petición que falla
  ok    se puede reintentar con la misma clave y se crea
  ok    una sola vez
  ok    una petición rechazada por el negocio
  ok    no deja la clave anotada: se puede corregir y reintentar
```

Lo que este taller deja para la sesión 05: dos peticiones **simultáneas** con la misma clave (el adaptador de esta sesión
confirmaría las dos). La 05 lo resuelve con un conflicto al confirmar y lo prueba con ocho hilos.

## Paso 7. Bandeja de salida

`publicar` escribe en el cuaderno; `confirmar` lo pasa a la bandeja; `entregarPendientes` es el mensajero.

```
Bandeja de salida (paso 7):
  ok    un pedido rechazado
  ok    y uno que falla a mitad
  ok    no dejan eventos pendientes
  ok    si el destino está caído, la entrega falla
  ok    pero el evento no se pierde: sale en el siguiente intento
  ok    con un id estable
  ok    y no sale dos veces
```

## Paso 8. El correo de "confirmar pedido"

Va **después** de confirmar la transacción, a través de la bandeja: el caso de uso publica `PedidoConfirmado` en la misma
transacción y el mensajero, al entregarlo, envía el correo (en la sesión 06 `Main` lo hace así).

| Alternativa | Qué ocurre |
|---|---|
| Enviar el correo dentro de la transacción, antes de confirmar | Si la confirmación falla (conflicto, caída), el cliente recibió "tu pedido está confirmado" de un pedido que sigue pendiente. El correo no se deshace. Además la transacción espera al servidor de correo con recursos retenidos |
| Enviarlo después de confirmar, directamente desde el caso de uso | Si el proceso cae entre la confirmación y el envío, el correo no sale nunca. Si el envío falla, ¿se reintenta? ¿quién? |
| Bandeja de salida (la elegida) | El evento se guarda con el pedido: o están los dos o ninguno. El mensajero reintenta hasta entregar. Precio: el correo puede salir más de una vez (al menos una vez), por eso el evento lleva un id estable para que el destino descarte repeticiones |

## Paso 9. `catch` dentro de la unidad de trabajo

No hay ninguno en `ServicioDePedidos` (compruébalo con `grep catch`). Si lo hubiera, por ejemplo capturando el fallo
de `pedidos.guardar` para "seguir", el `Supplier` terminaría bien y `confirmar` haría visibles las existencias descontadas
sin pedido: exactamente el defecto del paso 1, ahora con transacción.

## Criterios de aceptación

| Criterio | Resultado |
|---|---|
| Un fallo a mitad de "crear pedido" no deja existencias descontadas | Paso 4: ok |
| La misma petición dos veces crea un solo pedido | Paso 6: ok |
| Una petición fallida se puede reintentar con la misma clave | Paso 6: ok |
| Un pedido rechazado no deja eventos pendientes | Paso 7: ok |
| El dominio no menciona transacciones | `grep -ri transacc src/tienda/dominio` no encuentra nada; además `diff -r` con el dominio de la sesión 02: idéntico |
