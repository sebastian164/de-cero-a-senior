# Clasificación del estado de la aplicación del taller (punto 1 y 2)

Aplicación: la página `/n5-02/taller` (pedidos, carrito, productos).

| Estado | Tipo | Dónde vive | Antes → después |
|---|---|---|---|
| Estado de la lista de pedidos (todos, pendiente, pagado, enviado) | URL | `?estado=` | En un servicio de la lista → entrada del componente de la ruta |
| Página de la lista | URL | `?pagina=` | En un servicio → URL (el atrás y recargar funcionan) |
| Pestaña activa (lista / resumen) | URL | `?pestana=` | Campo del componente → URL (enlace compartible) |
| Pedidos de la página actual | Servidor | `GET /api/pedidos` | Respuesta de la petición (no se copia a un almacén) |
| Productos | Servidor | `CacheServidor`, clave `productos` | Copia local sin vigencia → caché con vigencia, deduplicación e invalidación |
| Líneas del carrito | Compartido de cliente | `CarritoServicio` / `CarritoStore` | Peldaño 4 y 5 comparados |
| Total y cantidad del carrito | Derivado | `computed` | No se guardan |
| Texto de un campo, desplegable abierto | Interfaz local | Señal del componente | Sin cambios |
| Valores y errores de un formulario | Formulario | El formulario (sesión n4-02) | Sin cambios |

Los tres datos movidos a la URL (punto 2): estado, página y pestaña. El dato duplicado unificado (punto 3): la cantidad del resumen, guardada además de las líneas (`duplicado.ts`); `estado.spec.ts` muestra que la copia miente tras quitar una línea y que el `computed` no.
