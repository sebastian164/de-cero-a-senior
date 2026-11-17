# Taller nivel-02/04: ISP. Mediciones y decisiones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-02-solid/04-isp/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.tsx`. Código: `componentes.tsx` (propiedades), `contextos.tsx` (contexto único frente a separados), `menu-completo.tsx` (el objeto completo cruza la frontera).

## Punto 1. Propiedades reducidas a lo que se usa

| Componente | Antes (recibe) | Después (recibe) |
|---|---|---|
| `Avatar` | `usuario` completo (40 campos) | `src` y `alt` |
| `Precio` | `producto` completo (costo, proveedor, existencias…) | `nombre` y `precio` |
| `InsigniaRol` | `usuario` completo | `esAdministrador: boolean` |

Prueba: el `innerHTML` de los tres componentes con las propiedades reducidas es **idéntico** al de la versión de partida (mismo resultado, menos acoplamiento: `Precio` ya no se entera de que existe un costo ni un proveedor).

## Punto 2. Dividir el contexto: renderizados medidos

Se cuenta cuántas veces se renderiza el interruptor de tema (memorizado, para aislar el efecto del contexto) mientras se pulsa 3 veces el botón del carrito. Contador de renderizados dentro del componente
(la alternativa sin mutar es `<Profiler onRender>`, usada en nivel-03/03 punto 4, donde se hace la misma medición):

| Contexto | Renderizados del interruptor de tema tras 3 cambios del carrito |
|---|---|
| único (tema + carrito juntos) | **3** (uno por cada cambio del carrito, aunque el tema no cambió) |
| separados (tema, cambiar tema, carrito, agregar) | **0**; solo vuelve a renderizar (1) cuando se cambia el tema |

## Punto 3. Qué datos cruzan del servidor al cliente

Se midió en la respuesta HTML que recibe el navegador (los datos de un componente de cliente viajan serializados en la carga de Next). `e2e/n5-01.spec.ts`, punto 2:

| Pantalla | Qué recibe el componente de cliente | ¿Aparece `costoProveedor` o la nota del proveedor en la respuesta? |
|---|---|---|
| `/n5/01-servidor-cliente/fuga` | el registro completo del producto | **sí** (ambos) |
| `/n5/01-servidor-cliente/proyectada` | solo los campos que pinta | no |
| `/n5/01-servidor-cliente/productos/1` (ficha real) | solo lo necesario | no |

Se recortó proyectando en el servidor (`const { nombre } = PRODUCTOS[0]`; solo ese campo se pasa a `FichaDeCliente`) antes de entregar nada a un componente de cliente. `menu-completo.tsx` es la versión de partida de nivel-02/04: recibe un `Usuario` completo para pintar `usuario.nombres`; todo el usuario (documento, correo, perfil) se serializa hacia el navegador.

## Punto 4. Un caso donde pasé el objeto completo, a propósito

`FilaProducto({ producto, onElegir })` (en `componentes.tsx`). Pinta cuatro campos (`nombre`, `sku`, `precio`, `existencias`) y además **entrega el producto entero** a quien lo elige (`onElegir(producto)`; la prueba comprueba que se llama con el mismo objeto).
Reducirlo a cuatro propiedades sueltas obligaría a quien llama a reconstruir el producto para `onElegir`, y la firma cambiaría cada vez que el producto gane un campo que la fila no pinta pero sí entrega.
ISP pide no depender de lo que no se usa; aquí el objeto entero sí es la interfaz que la fila necesita (lo recibe para devolverlo). Contraste con `PrecioAntes`: recibe el `Producto` solo para leer dos campos, y eso sí se reduce a `{ nombre, precio }`.
