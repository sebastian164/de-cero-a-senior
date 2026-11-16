# Taller 05-01 — Separación de responsabilidades (solución)

Endpoint tomado: el `PedidoController` del ejemplo del README. El código separado está en `src/main/java/tienda` y se ejecuta con `mvn test` (8 pruebas: 3 de dominio sin Spring y 5 de la aplicación con MockMvc, seguridad y H2).

## 1. Cada línea, etiquetada

| Línea del endpoint original | Asunto |
|---|---|
| `@PostMapping("/pedidos")`, firma con `Map` y `HttpServletRequest` | Presentación (canal HTTP, contrato) |
| `if (!"ADMIN".equals(request.getHeader("X-Rol"))) throw ...` | Transversal: seguridad |
| `long total = cantidad * 40_000` | Dominio: regla de precio |
| `if (total > 1_000_000) total = total * 90 / 100` | Dominio: descuento por volumen |
| `jdbc.update("INSERT INTO pedidos ...")` | Infraestructura: persistencia |
| `log.info("Pedido creado " + body)` | Transversal: registro (además concatena y vuelca el cuerpo entero) |
| `return Map.of("total", total, "mensaje", "Creado")` | Presentación: formato de respuesta |

## 2. Reescritura

| Asunto | Clase |
|---|---|
| Presentación | `adaptadores/web/PedidoController`, `CrearPedidoRequest` (validación de formato), `PedidoResponse` |
| Aplicación | `aplicacion/CrearPedido` (transacción), puerto `RepositorioPedidos` |
| Dominio | `dominio/Pedido` (precio y descuento, sin importar nada técnico) |
| Infraestructura | `adaptadores/persistencia/RepositorioPedidosJdbc` (único lugar con SQL) |
| Transversal | `SeguridadConfig` + `@PreAuthorize("hasRole('ADMIN')")` |
| Segundo canal | `adaptadores/mensajeria/ConsumidorPedidos` |

Pruebas que lo demuestran: `PedidoTest` prueba el descuento (umbral exacto incluido) sin Spring; `PedidoApiTest` verifica 201, 401, 403 y 400, y que el consumidor de mensajes calcula el mismo total.

## 3. Qué se toca antes y después

| Cambio | Antes (un método) | Después |
|---|---|---|
| Cambiar la regla de descuento | El controlador (y cualquier copia de la regla en otros canales) | Solo `Pedido` |
| Exponer la operación por mensajería | Copiar la regla, la seguridad y el SQL a otra clase | Agregar un adaptador (`ConsumidorPedidos`, 10 líneas); dominio y aplicación intactos |
| Cambiar de base de datos | El controlador | Solo el adaptador de persistencia |
| Cambiar el formato de la respuesta | El controlador (mezclado con la regla) | Solo `PedidoResponse` |

## 4. Atributos de calidad (sistema de ventas por volumen, equipo pequeño)

1. **Corrección de las reglas de precio**: un error de descuento es pérdida de dinero directa y silenciosa. Por eso la regla vive en el dominio con pruebas propias.
2. **Seguridad**: crear pedidos es una operación privilegiada; el original la verificaba con una cabecera falsificable.
3. **Facilidad de cambio**: el negocio ajusta descuentos y canales varias veces al año.
4. **Disponibilidad**: las ventas se pierden si el endpoint cae, pero una caída corta se tolera (hay reintentos del cliente).
5. **Rendimiento**: el volumen es bajo (decenas de pedidos por minuto); no condiciona el diseño hoy.

El orden sigue el costo de equivocarse: un descuento mal aplicado o un acceso indebido cuestan más que unos milisegundos.
