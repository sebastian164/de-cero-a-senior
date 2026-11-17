# Taller nivel-03/02: hooks personalizados. Comparaciones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-03-patrones/02-hooks-personalizados/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.tsx`. Código: `hooks.ts` (71 líneas: `useInterruptor`, `useDebounce`, `useVisibleEnPantalla`, `useFormulario`), `tabla.ts` (11, lógica pura), `use-tabla.ts` (15), `ui/` (componentes).

## Punto 2. `useFormulario` propio frente a React Hook Form

Los dos formularios del taller (contacto y cambio de clave) usan el mismo `useFormulario`; el de clave se repite con `react-hook-form` y la prueba exige **el mismo resultado observable** (errores solo en campos tocados, "Mínimo 12 caracteres", y mensaje de éxito).

| | `useFormulario` propio (26 líneas) | `react-hook-form` 7.92.0 |
|---|---|---|
| Código a mantener | 26 líneas nuestras | 0 propias, una dependencia con su propio ciclo de versiones |
| Campos no controlados y menos renderizados | no: cada tecla actualiza el estado y renderiza el formulario | sí: registra el DOM y renderiza solo lo que observa |
| Validación entre campos (`repetir` contra `clave`) | función que recibe todos los valores | `validate: (v) => v === watch('clave')`; el compilador de React omite ese componente (`react-hooks/incompatible-library`, aviso del linter anotado en el código) |
| Listas dinámicas, formularios anidados, esquemas Zod | no existen | `useFieldArray`, `zodResolver` |
| Prerender con Cache Components (Next 16) | sin efectos de lectura de hora ni aleatorios | el formulario con `useFieldArray` (nivel-04/02) abortó el prerender de toda la página; se aisló con una carga solo en el cliente (ver el ANALISIS de nivel-04/02) |

Conclusión: el propio basta para dos o tres formularios simples; en cuanto aparecen listas dinámicas o esquemas compartidos, la biblioteca ahorra más de lo que cuesta.

## Punto 3. Lógica extraída: primero funciones puras, luego hooks

Lógica de una tabla filtrable (filtrar por cliente, ordenar por cliente o total y paginar). Primero `tabla.ts` (11 líneas puras: `filtrar` y `ordenar`; la paginación es el hook del README, `usePaginacion`), después `use-tabla.ts` (15 líneas, solo estado y combinación) y la interfaz (`ui/tabla-pedidos.tsx`, 25 líneas) queda sin reglas.

## Punto 4. Pruebas

Lógica pura sin React ("funciones puras"); hooks con `renderHook` (`usePaginacion` no queda fuera de rango si la lista se acorta; `useOrdenado` usa la estrategia recibida; `useDebounce` entrega solo el último valor tras el silencio, con relojes simulados).

## Punto 5. La biblioteca de datos detrás de hooks de dominio

`usePedidos` envuelve a TanStack Query 5.107.1; `TablaPedidos` la usa sin importar la biblioteca. Una prueba lee los archivos de `ui/` y exige que **ninguno** importe `@tanstack/react-query`: si alguien lo hace, la prueba falla.
