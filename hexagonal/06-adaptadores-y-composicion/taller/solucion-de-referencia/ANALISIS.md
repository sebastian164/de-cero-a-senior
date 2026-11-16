# Taller 06 — Adaptadores y composición de los pedidos

Proyecto en esta carpeta: el núcleo de la sesión 05 sin cambios (compara `dominio/` y `aplicacion/` con
`diff -r`), más `adaptadores/entrada/http/ServidorHttp`, `adaptadores/entrada/consola/Consola` y `Main`. Compilado con
`javac -Xlint:all -Werror` y ejecutado con JDK 21.0.13: **137 comprobaciones, 0 fallos**. `Main` se puede arrancar en modo consola y en modo HTTP
(`java -cp salida tienda.Main http 8080`) y atender peticiones con `curl`.

## Paso 1. Adaptador HTTP

| Método y ruta | Puerto de entrada | Respuesta |
|---|---|---|
| `POST /pedidos`, cabecera `Idempotency-Key` | `CrearPedido` | 201 si es nuevo, 200 si la clave ya se atendió (el mismo pedido) |
| `POST /pedidos/{id}/confirmacion` | `ConfirmarPedido` | 200 |
| `POST /pedidos/{id}/cancelacion` | `CancelarPedido` | 200 |
| `GET /pedidos/{id}` | `ConsultarPedido` | 200 |
| `GET /pedidos?cliente=…` | `ListarPedidosDeCliente` | 200, lista |

Usa el servidor del JDK **con** un ejecutor de 8 hilos: el código de inicio de la sesión 01 no tenía ejecutor y atendía una
petición tras otra (por eso no mostraba la carrera).

## Paso 2. Traducción de excepciones

| Excepción del núcleo | HTTP | Por qué |
|---|---|---|
| `ReglaDeNegocioViolada` (incluye `ProductoDesconocido`) | 422 | La petición está bien formada pero el negocio la rechaza; repetirla igual dará lo mismo |
| `PedidoNoEncontrado` | 404 | El recurso de la ruta no existe |
| `ConflictoDeConcurrencia` | 409 | Otro cambió lo mismo y se agotaron los reintentos; el cliente puede reintentar con la misma clave |
| `IllegalArgumentException` (falta la clave, cantidad que no es número, línea sin `:`) | 400 | La entrada no tiene la forma esperada; ni siquiera llegó al núcleo |
| Cualquier otra | 500, `{"error":"error interno"}` | Un fallo técnico; los detalles no salen hacia fuera |

Cada fila tiene su prueba en `PruebasDeAdaptadores` (el 409 con `InventarioConInterferencia`, el 500 con un catálogo que
lanza `IllegalStateException`).

## Paso 3. Segundo adaptador

`Consola`: `crear <clave> <cliente> <producto:cantidad>…`, `confirmar`, `cancelar`, `ver`, `pedidos <cliente>`. Habla en
pesos. Usa los mismos cinco puertos de entrada.

## Paso 4. Raíz de composición

`tienda.Main` es el único archivo de `src` que hace `new` de adaptadores (`AlmacenEnMemoria`, `CatalogoEnMemoria`,
`RelojDelSistema`, `ServidorHttp`, `Consola`). Se comprueba con `grep`. Arranca además el mensajero de la
bandeja de salida, que "envía el correo" (`[correo a ana] PedidoConfirmado pedido-1`).

## Paso 5. Una regla, antes y ahora

"No se vende más de lo que hay": en `Pedidos.java` era `if (EXISTENCIAS.get(partes[0]) < cantidad)` en la línea 62, dentro
del bucle que lee el formulario y descontando línea a línea (de ahí el defecto). Ahora es `Existencias.reservar` y
`Reserva.reservar` en el dominio (todas las líneas se comprueban antes de descontar), el caso de uso la invoca dentro de
una unidad de trabajo, y el adaptador HTTP solo traduce su rechazo a 422.

## Paso 6. Pruebas del adaptador HTTP con peticiones reales

Camino feliz (201 y el JSON completo), rechazo por regla (422), producto desconocido (422), recurso inexistente (404),
entrada mal formada (400), petición repetida (200 con el mismo cuerpo), sin clave (400), confirmar dos veces (422), JSON
(201), ruta desconocida (404), 409 y 500.

## Paso 7. Un pedido creado por un adaptador se ve desde el otro

La consola ve el pedido creado y confirmado por HTTP, y la clave `k-1` usada por HTTP figura como atendida en la consola;
HTTP (`GET /pedidos?cliente=marta`) ve el pedido creado y cancelado por la consola.

## Paso 8. Cambiar el formato de entrada

El adaptador acepta formulario y JSON según `Content-Type`. Todo lo del JSON está en `ServidorHttp.java`: los dos `Pattern`,
`desdeJson` y la elección en `despachar`. Más una prueba en `PruebasDeAdaptadores`. Ningún otro archivo cambió:
Se comprueba que en `dominio/` y `aplicacion/` no aparece la palabra `json` y que esas carpetas son idénticas a
las de la sesión 05.

## Paso 9. Comparaciones con valores del negocio en los adaptadores

Ninguna. Los únicos `if` de `ServidorHttp` comparan métodos HTTP, rutas, la presencia de la cabecera y el `Content-Type`;
los de `Consola`, el nombre de la orden. El adaptador no sabe del tope, del estado ni de las existencias.

## Criterios de aceptación

| Criterio | Resultado |
|---|---|
| Los adaptadores de entrada solo dependen de puertos de entrada | Prueba de arquitectura: ok |
| Ningún adaptador contiene reglas del negocio | Paso 9 |
| Solo la raíz de composición crea adaptadores | `grep` sobre `src`: solo `Main.java` |
| Cada excepción del núcleo tiene su traducción | Tabla del paso 2 y sus pruebas |
| Cambiar el formato de entrada no tocó el núcleo | Núcleo idéntico al de la sesión 05 |
