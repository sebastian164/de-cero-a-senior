# Taller nivel-04/01: clean code en React. Qué se quitó, qué dijo el linter y qué se midió

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-04-clean-code-y-testing/01-clean-code-en-react/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Partida: `antes/panel-pedidos.tsx` (50 líneas). Resultado: `despues/panel-pedidos.tsx` (48 líneas) y `despues/dominio.ts` (10 líneas de reglas puras). Pruebas: `taller.test.tsx` (10). Mediciones: `mediciones/vitest.jsonl`.

## Puntos 1 a 4. Cambios aplicados al panel

| Problema del "antes" | Cambio |
|---|---|
| `total` y `pendientes` eran estado, recalculados en un efecto | valores derivados al renderizar (`sumarTotal`, `pendientesDe`) |
| `enviado` + un efecto que reacciona a `enviado` para marcar pagados | el manejador `cobrar` hace el cambio directamente (`cobrarPendientes`) |
| `cargando`, `error` y `pedidos` como tres estados que pueden contradecirse | una unión discriminada `{ tipo: 'cargando' } \| { tipo: 'error'; mensaje } \| { tipo: 'lista' }` |
| condición `usuario && usuario.rol === 'admin' && pendientes.length > 0 && !error` sin nombre | `puedeCobrar(usuario, pendientes)` |
| `useMemo`, `useCallback` sin medición | quitados (punto 6) |
| `pendientes.length && <p>…</p>` pintaba un **0** suelto cuando no había pendientes | `pendientes.length > 0 && …`; prueba "antes: … pinta un 0 suelto" frente a "después: no pinta nada extra" |
| `<div onClick>` para cobrar | `<button type="button">`; la prueba comprueba que el "antes" no tiene rol de botón |
| `key={i}` | `key={p.id}` |

## Punto 5. Qué dice el linter del "antes" (reglas de hooks y accesibilidad como `error`)

`eslint --no-ignore` sobre `antes/panel-pedidos.tsx` (`eslint.config.mjs` activa `rules-of-hooks`, `exhaustive-deps` y las reglas `jsx-a11y` como error):

| Regla | Veces |
|---|---|
| `react-hooks/set-state-in-effect` | 3 (los tres efectos que copian estado) |
| `react-hooks/exhaustive-deps` | 1 (el efecto de carga sin `cargar` en sus dependencias) |
| `jsx-a11y/click-events-have-key-events` | 1 |
| `jsx-a11y/no-static-element-interactions` | 1 (el `div` clicable) |

El panel limpio no dispara **ninguna** y su archivo no contiene `eslint-disable` (prueba: "no silencia nada"). No hay un bloque general que apague las reglas del compilador de React para los talleres; los pocos casos legítimos que quedan (contadores de renderizados que el taller mide, `watch()` de react-hook-form, el virtualizador) llevan su `eslint-disable-next-line` con el motivo en la propia línea.

## Punto 6. Memorización medida

`PanelConReloj` pinta una lista de 2000 filas con trabajo costoso por fila y un botón "Tic" que cambia el estado del padre; se pulsa 5 veces. Duración media de renderizado por actualización (`Profiler`, jsdom en modo desarrollo; `mediciones/vitest.jsonl`):

| Lista sin `memo` | Lista con `memo` |
|---|---|
| 9,47 ms | 0,04 ms |

Con una lista costosa que no depende del reloj, `memo` evita el trabajo (la prueba exige que baje a menos de un tercio). La memorización se repone solo ahí, no en el panel limpio, donde la medición no la justifica. Los valores absolutos son de jsdom en desarrollo: la relación importa, no los milisegundos.
