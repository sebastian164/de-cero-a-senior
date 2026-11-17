# Taller n3-03 — Facade y servicios de estado

| # | Solución | Prueba |
|---|---|---|
| 1 | `almacen-carrito.ts › AlmacenCarrito`: estado privado en una señal; `lineas`, `cantidad`, `subtotal`, `total` como `computed`; `agregar`, `quitar`, `cambiarCantidad`, `vaciar` como intenciones | `almacen.spec.ts` |
| 2 | `IconoCarrito` (encabezado) y `PaginaCarrito` leen el mismo almacén | `componentes.spec.ts` y E2E (dos contadores iguales) |
| 3 | `cambiar()` aplica el cambio, guarda con `PUT /api/carrito` y, si falla, restaura el estado anterior | `almacen.spec.ts` › 3 con un `Subject` que falla; E2E con el interruptor "Simular fallo del servidor" (el servidor responde 503) |
| 4 | Doble de `CarritoApi` que devuelve un `Subject` controlado por la prueba | `almacen.spec.ts` |
| 5 | Filtro de categoría en `?categoria=` y leído como entrada (`withComponentInputBinding`) | E2E: el enlace "Monitores" cambia la URL; recargar conserva el filtro; atrás vuelve a "Todas" |

## 5. Qué se gana con el filtro en la URL

- **Se puede compartir y sobrevive a recargar:** `/n3-03/taller?categoria=monitores` abre ya filtrado (la E2E recarga y lo comprueba).
- **El botón atrás deshace el filtro**, sin código.
- **Una sola fuente de verdad:** antes el filtro estaba en el almacén y en la URL había que sincronizarlo; ahora el almacén no sabe de filtros (son estado de la vista, no del carrito).

Detalle: la entrada `categoria` se declara `input<string | undefined>()` sin valor por defecto, porque con `withComponentInputBinding()` una entrada que la URL no trae se pone en `undefined` y un valor por defecto se perdería.
