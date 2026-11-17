# Taller nivel-03/03: contexto, reductores y estado. Mediciones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-03-patrones/03-contexto-reductores-y-estado/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.tsx` (14). Código: `carrito.tsx` (proveedor con persistencia y cupones), `reductor-ampliado.ts`, `componentes.tsx`, `composicion.tsx`.

## Punto 3. Qué se renderiza al agregar un producto (3 agregados)

Se cuenta con un contador dentro del botón memorizado (y con `<Profiler>` en el punto 4).

| Estructura | Renderizados del botón "Agregar" tras 3 agregados | Renderizados de quien muestra el total |
|---|---|---|
| Un solo contexto `{ estado, despachar }` | **3** (cada cambio de estado cambia el valor del contexto) | 3 |
| Dos contextos (estado y acciones, como el README) | **0** | 3 |
| Carrito ampliado (cupones y persistencia, mismos componentes) | **0** | 3 |

## Punto 4. Almacén externo con selectores (zustand 5.1.0) frente a contexto

`ContadorCarrito` selecciona `lineas.length`. Tres agregados del mismo producto cambian la cantidad de esa línea (1, 2, 3) pero no el número de líneas: el `Profiler` mide **1** renderizado en total (el montaje), no 4.

| | Contexto dividido | Almacén con selectores |
|---|---|---|
| Renderizados evitados | solo los que dependen de un contexto distinto | los que dependen de otra porción cualquiera del estado |
| Proveedor y árbol | necesita `Provider` | no |
| Dependencia nueva | ninguna | zustand |
| Estado global singleton | no: una instancia por proveedor | sí: se comparte entre pruebas y puede filtrarse entre peticiones en el servidor; hay que reiniciarlo (`vaciar()` en la prueba) |
| Pruebas | basta un `Provider` nuevo por prueba | hay que limpiar el almacén |

## Punto 2. Cupones y persistencia (detalle)

Cupones: `BIENVENIDA10` (10 %, sobre 18.000 descuenta 1.800) y `MENOS5000` (fijo, mínimo 30.000: con un café no aplica; con dos descuenta 5.000; el descuento nunca supera el subtotal). Persistencia: lo leído de `localStorage` se valida con Zod antes de entrar al estado (un JSON roto, un precio negativo o un objeto en vez de arreglo se descartan); lo guardado coincide con lo mostrado.

## Punto 5. Taladro de propiedades frente a composición

`PaginaConTaladro` pasa `usuario` por `Diseno`, `Barra` y `AvatarUsuario` (solo el último lo usa). `PaginaCompuesta` arma el `AvatarUsuario` en la página y lo entrega como hueco; `Diseno` y `Barra` ya no conocen al usuario. La prueba comprueba que el HTML de ambas es **idéntico**.
