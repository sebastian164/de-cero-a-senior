# Taller 03 — Puertos y casos de uso de los pedidos

Proyecto en esta carpeta (el dominio de la sesión 02 sin cambios, más `aplicacion/` y adaptadores de memoria). Compilado con
`javac -Xlint:all -Werror` y ejecutado con JDK 21.0.13: **66 comprobaciones, 0 fallos**.

## Paso 1. Operaciones y puertos de entrada

| Operación | Puerto | Entra | Sale |
|---|---|---|---|
| Crear un pedido | `CrearPedido` | `Solicitud(cliente, [Linea(producto, cantidad)])` | `VistaDePedido` |
| Confirmarlo | `ConfirmarPedido` | id | `VistaDePedido` |
| Cancelarlo | `CancelarPedido` | id | `VistaDePedido` |
| Consultarlo | `ConsultarPedido` | id | `Optional<VistaDePedido>` |
| Listar los de un cliente (paso 8) | `ListarPedidosDeCliente` | cliente | `List<VistaDePedido>` |

## Paso 2. Qué necesita cada caso de uso del exterior

| Frase | Puerto de salida |
|---|---|
| "Necesito un id nuevo, guardar el pedido y encontrarlo después" | `RepositorioDePedidos` |
| "Necesito saber cuánto cuesta hoy cada producto" | `Catalogo` |
| "Necesito saber cuántas unidades hay y dejar anotadas las que quedan" | `Inventario` |
| "Necesito saber qué hora es" | `Reloj` |
| "Necesito avisar de que se creó, confirmó o canceló" | `PublicadorDeEventos` |

## Paso 3. Precios y existencias: dos puertos

Dos. Los precios solo se **leen** y es normal que mañana vengan de otro servicio (un catálogo); las existencias se leen
**y se escriben**, y deben cambiar en la misma transacción que el pedido (sesión 04). Un solo puerto obligaría a que el
mismo adaptador sirviera a las dos fuentes, o a que el catálogo remoto entrara en la transacción del pedido. Con dos,
`CatalogoEnMemoria` y el almacén de pedidos y existencias evolucionan por separado (y así se mantuvo hasta la sesión 07).

## Paso 4. Los `if` del servicio

`ServicioDePedidos` (sesión 03) no tiene ningún `if`. Lo que podría haber sido uno está en el dominio: cantidad mínima en
`LineaDePedido`, cliente, líneas y tope en `Pedido.crear`, existencias en `Reserva.reservar`, transiciones en `Pedido`.
El único "rechazo" que nace en la aplicación es `ProductoDesconocido` (el catálogo no tiene el producto), que es la
traducción de un `Optional` vacío de un puerto, no una regla.

## Paso 5. Adaptadores de memoria

`PedidosEnMemoria`, `InventarioEnMemoria`, `CatalogoEnMemoria`, `EventosEnMemoria` y `RelojDelSistema`. Los repositorios
guardan y entregan copias.

## Paso 6. Pruebas de cada caso de uso

`PruebasDeLaAplicacion`: crear (camino feliz, sin existencias, producto desconocido, sin cliente, sin líneas, cantidad cero,
sobre el tope, y que ningún rechazo descontó existencias ni publicó eventos), confirmar (feliz, dos veces, inexistente),
cancelar (de confirmado, devuelve existencias, dos veces, inexistente), consultar (existe, no existe) y listar.

## Paso 7. ¿Algún puerto devuelve un objeto del dominio modificable?

No: todos devuelven `VistaDePedido`, un `record` con líneas inmutables (`List.copyOf`). Probado: `lineas().add(...)` lanza
`UnsupportedOperationException`. Ningún puerto de entrada recibe ni devuelve `Pedido`.

## Paso 8. "Listar los pedidos de un cliente"

| Archivo | Capa |
|---|---|
| `aplicacion/puertos/entrada/ListarPedidosDeCliente.java` (nuevo) | Aplicación, puerto de entrada |
| `aplicacion/ServicioDePedidos.java` (un método) | Aplicación |
| `aplicacion/puertos/salida/RepositorioDePedidos.java` (`deCliente`) | Aplicación, puerto de salida |
| `adaptadores/salida/memoria/PedidosEnMemoria.java` | Adaptador de salida |
| `pruebas/tienda/PruebasDeLaAplicacion.java` | Pruebas |

Cuatro archivos de producción; ninguno del dominio.

## Paso 9. La pregunta pendiente

Está como comentario `PENDIENTE (sesión 04)` en `ServicioDePedidos.crear`, entre `inventario.guardar` y `pedidos.guardar`.
La sesión 04 la responde con una prueba que la provoca.

## Criterios de aceptación

| Criterio | Resultado |
|---|---|
| La aplicación no importa nada de adaptadores | Prueba de arquitectura ("la aplicación solo depende del dominio, de sí misma y del JDK"): ok |
| Los puertos de salida no nombran ninguna tecnología | Nombres: `RepositorioDePedidos`, `Catalogo`, `Inventario`, `Reloj`, `PublicadorDeEventos`; métodos en vocabulario del negocio |
| Cada caso de uso tiene pruebas con adaptadores de memoria | `PruebasDeLaAplicacion`: 28 comprobaciones |
| No quedan reglas del negocio en el servicio | No hay `if` en el servicio |
