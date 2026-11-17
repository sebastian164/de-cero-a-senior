# Taller nivel-01/01: lista de tareas. Lo que había que describir

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-01-fundamentos/01-fundamentos-de-react/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Todo se comprueba con `taller.test.tsx` (Vitest 4.1.12, jsdom, Testing Library). Código: `tareas.ts` (lógica pura), `lista-tareas.tsx` (página con el estado, lista y elemento), `clave-indice.tsx`.

## Punto 5. El índice como clave: qué se rompe

`ListaConNotas` dibuja tres personas (Ana, Luis, Eva), cada una con un campo de texto no controlado. Se renderiza dos veces: con `key={i}` y con `key={p.id}`. En ambas se escribe "nota de Ana" en el campo de Ana y se quita a Ana.

| Clave | Campo "Nota para Luis" después de quitar a Ana |
|---|---|
| índice (`key={i}`) | **"nota de Ana"**: Luis hereda el texto de Ana |
| `id` (`key={p.id}`) | vacío, como corresponde |

Por qué: al quitar la fila 0, la fila que queda en la posición 0 ahora es Luis, pero su clave sigue siendo `0`. React concluye que es el mismo elemento, lo reutiliza y conserva el estado del DOM del `<input>`
(el texto escrito, el foco, la selección). Lo que cambia es la etiqueta, no el campo. Pasa solo con estado que React no controla (campos no controlados, foco, animaciones, estado interno del hijo); con un campo controlado desde el estado de la lista no se notaría, y por eso el error pasa desapercibido hasta que alguien lo escribe en producción.
Prueba: "con el índice como clave, la nota escrita queda en la fila equivocada".

## Punto 6. Lógica pura sin React

`tareas.ts` exporta `agregar`, `alternar`, `eliminar`, `filtrar` y `contarPendientes`. La prueba "lógica pura, sin React" las ejercita sin importar React: `agregar` no modifica el arreglo original (se compara
la referencia y el contenido) e ignora títulos vacíos; `alternar`, `eliminar`, `filtrar` y el contador se comprueban caso por caso. El contador de pendientes y la lista filtrada son valores derivados:
se calculan al renderizar a partir de `tareas` y `filtro`, no son estado propio (punto 2).

## Resultado

Tres componentes (página con el estado, `Lista`, `Elemento`), todo tipado y sin `any` (lo comprueba `tsc --strict`, con `npm run typecheck`).
