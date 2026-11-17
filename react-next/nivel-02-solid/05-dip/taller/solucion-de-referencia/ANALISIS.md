# Taller nivel-02/05: DIP. Dependencias y decisiones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-02-solid/05-dip/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.tsx`. Código: `casos-de-uso.ts` (caso de uso de servidor con sus dependencias por parámetro), `composicion.ts` (único módulo que conoce las implementaciones), `demo-cliente.tsx` (ensamblaje en la raíz del cliente).
Los componentes y la lógica del README están en `proyectos/react-next/n2-05-dip/`.

## Punto 1. Dependencias ocultas del `BotonAgregar` de partida

| Dependencia oculta | Qué la delata | Consecuencia |
|---|---|---|
| `localStorage` (navegador) | `localStorage.getItem('carrito')` dentro del manejador | no se prueba sin navegador (o hay que simular el global) |
| El reloj | `Date.now()` | el resultado cambia en cada ejecución: no se puede afirmar el valor |
| La analítica de un tercero | `(window as any).gtag(...)` con un `any` | cambiar de proveedor obliga a tocar el botón; sin el SDK cargado revienta |
| La red y su ruta | `fetch('/api/carrito', …)` | acoplado a una URL |

## Punto 2. La lógica con dependencias recibidas, sin navegador

`agregarAlCarrito(producto, { almacen, analitica, ahora })`. Prueba: se inyectan un almacén en memoria, una analítica que anota los identificadores y un reloj fijo (`1_700_000_000_000`).
Resultado: se acumula sobre lo guardado, cada línea queda con `agregadoEn` igual a la hora inyectada y se avisa a la analítica `['p1', 'p2']`. Sin tocar `localStorage` ni `gtag`: la prueba ni siquiera los menciona.

## Punto 3. Implementaciones por contexto, cambiadas en una prueba de componente

`ProveedorDeDependencias` da las implementaciones; en la prueba del componente se entregan las falsas y, tras pulsar "Agregar", el almacén falso tiene 1 línea, la analítica registró `['p9']` y `localStorage` real sigue sin la clave `carrito-dip`.
Sin proveedor, el componente falla con un mensaje claro: "Falta ProveedorDeDependencias".

## Punto 4. Módulo de composición para un caso de uso de servidor

`crearCancelarPedido(repositorio, reloj)` devuelve la función. `composicion.ts` (con `server-only`) es el único archivo que sabe qué repositorio y qué reloj reales se usan. Pruebas sin Next ni base de datos: cancela un pedido propio con menos de 24 horas;
rechaza el ajeno, el vencido (30 h) y el ya cancelado, con mensajes distintos.

## Punto 5. Qué decidí no abstraer

- **`Date.now` en `demo-cliente.tsx` (`ahora: Date.now`)**: se pasa tal cual; no se creó una clase `Reloj`. Una función `() => number` es toda la abstracción necesaria.
- **React y `useContext` mismos**: el ensamblaje usa el contexto de React directamente, sin una capa propia sobre él.
- **El repositorio de pedidos del taller**: la composición usa la implementación en memoria porque no hay base de datos en este ejercicio; el puerto (`RepositorioPedidosDip`) existe porque hay una segunda implementación (la de las pruebas), no por previsión de una tercera.
Regla aplicada: se inyecta lo que hace daño a la prueba (red, reloj, almacenamiento, terceros) o lo que cambia por decisión de negocio; no lo que es estable y local.
