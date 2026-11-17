# Taller frontend-senior/02: calidad, entrega y observabilidad. Resultados

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/frontend-senior/02-calidad-entrega-y-observabilidad/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Next.js 16.4.6, Node 26, React 19.3.1. Cada punto del taller con la prueba que lo respalda y el dato observado. Datos crudos en `mediciones/` de la aplicación (se generan al ejecutar las pruebas).

## Puntos 1 a 6

| Punto | Entregable | Prueba que lo respalda | Dato observado |
|---|---|---|---|
| 1. Flujo de CI con todas las puertas | `ci/calidad.yml` (lint, tipos, formato, pruebas, build con presupuesto, auditoría; PostgreSQL como servicio) | `taller.test.ts` (cubre cada puerta de la tabla del README y que cada script npm exista), `actionlint` y `act -j calidad` (herramienta `act`) | `act -j calidad` en contenedor: checkout, setup-node, `npm ci`, lint, typecheck, formato, `npm test` (33 archivos, 309 pruebas), `npm run build` y `npm audit` en verde, "Job succeeded" (registro de `act`, que no se incluye). El presupuesto de JavaScript gzip de la build: 354 724 bytes frente al límite de 400 000 |
| 2. Vista previa por pull request | `ci/vista-previa.yml` y `scripts/vista-previa.sh levantar/bajar <n>` | `taller.test.ts` (se dispara en cada PR, levanta al abrir o actualizar y baja al cerrar; ejecutor propio; comentario con la URL), `actionlint` y la ejecución de `scripts/vista-previa.sh` | Un contenedor y una imagen por PR en el puerto 49560 + n, con la imagen de salida independiente; responde en `/n5/04-renderizado/inicio` y al bajar se elimina todo |
| 3. Seguimiento de errores con source maps | `colector.ts`, `simbolizar.ts` y `src/fe/observabilidad.tsx` | `taller.test.ts` (traza, mapa, mapa ausente) y `e2e/fe-02.spec.ts` (a) con la build real | La pila minificada `chunks/07-pbebhscose.js:1:1932` vuelve a `src/fe/falla.ts:3:12` (`mediciones/fe-02.json`). Con Turbopack el mapa no se llama `archivo.js.map`: se lee `sourceMappingURL` |
| 4. Métricas web en campo | `src/n6-01/vitales.tsx` (biblioteca `web-vitals` 6.2.3, envío a `/api/vitales`) y validación en `colector.ts` | `e2e/n6-01.spec.ts` "punto 7: métricas web en campo frente a Lighthouse" y `taller.test.ts` (nombre, valor numérico y ruta obligatorios) | Campo (una visita local, sin limitación): LCP 20 ms, CLS 0, INP 16 ms, TTFB 4,3 ms. Laboratorio (Lighthouse con limitación): LCP 753 ms, puntaje 1 (`mediciones/n6-01.json`). Los dos números no son comparables: sirven para ver que la tubería de campo llega al colector |
| 5. Límites de error en dos secciones | `src/fe/observabilidad.tsx` con `react-error-boundary` | `e2e/fe-02.spec.ts` (b) y (d) | Al romper una sección la otra sigue viva y "Reintentar" la recupera; un error marcado como esperado no llega al colector y uno inesperado sí |
| 6. Bandera de función por porcentaje | `bandera.ts` (hash estable de persona y bandera) | `taller.test.ts` (0 % y 100 %, estabilidad, subir el porcentaje solo suma, banderas distintas reparten distinto, valores raros) y `e2e/fe-02.spec.ts` (c) | Al 30 %, en una muestra de 1000 personas, 302 la tienen (30,2 %); el cambio se hace con un POST, sin desplegar |

## Punto 7. Revisión de dependencias

Peso: gzip de `import * as m from '<paquete>'` empaquetado con esbuild para navegador, minificado, con React fuera (`scripts/pesos-dependencias.mjs`, resultado en `mediciones/pesos-dependencias.json`). Es una cota superior: con
importaciones nombradas el empaquetador descarta parte. Para el conjunto de la aplicación el dato real es el presupuesto de `scripts/presupuesto.mjs`: 59 archivos, 354 724 bytes gzip. Mantenimiento: última publicación en el registro de npm
consultada el 17 de noviembre de 2026 (`npm view <paquete> time.modified`); todas tienen publicaciones desde septiembre de 2026, salvo `server-only` (2022, es un paquete de una línea que solo lanza un error al importarlo desde el cliente).

| Dependencia | Versión | Peso gzip (cota) | Dónde se usa | Última publicación | Decisión y motivo |
|---|---|---|---|---|---|
| `zod` | 4.6.7 | 93 042 | 13 archivos: acciones de servidor, esquemas de formularios | 12 oct 2026 | **Se queda**, pero es la más pesada. En acciones de servidor no viaja al navegador; en el formulario con `react-hook-form` sí. Mantener los esquemas del lado servidor y evitar importarlos en componentes de cliente si no hace falta validar en el navegador |
| `react-hook-form` | 7.92.0 | 17 272 | 3 archivos de formularios del taller | 11 nov 2026 | **Se queda**: los formularios con listas dinámicas (`useFieldArray`) justifican la biblioteca. Un formulario de tres campos no la necesita |
| `marked` | 18.2.0 | 15 107 | 5 archivos | 16 nov 2026 | **Se queda solo en el servidor**: en la lista de libros del servidor no viaja; marcada toda como cliente añade unos 50 KB sin comprimir a la página (`mediciones/n1-03.json`). Nunca en un componente de cliente |
| `@tanstack/react-query` | 5.107.1 | 13 853 | 7 archivos, el proveedor | 6 nov 2026 | **Se queda** para el estado del servidor en componentes de cliente; si toda la lectura fuera de servidor se podría eliminar |
| `@tanstack/react-virtual` | 3.14.26 | 10 862 | 1 archivo (`filas.tsx`) | 3 nov 2026 | **Se queda**, con carga diferida: solo la usa la pantalla de 10 000 filas (95 ms frente a 37 ms y 10 000 nodos frente a 23). No debería estar en el paquete inicial |
| `web-vitals` | 6.2.3 | 3 377 | 1 archivo (`vitales.tsx`) | 5 oct 2026 | **Se queda**: es la forma estándar de medir en campo y pesa poco |
| `@hookform/resolvers` | 7.0.6 | 1 218 | 2 archivos | 11 nov 2026 | **Se queda** mientras exista `react-hook-form` con Zod |
| `react-error-boundary` | 6.1.6 | 1 033 | 3 archivos | 20 sep 2026 | **Se podría sustituir** por una clase de 15 líneas con `getDerivedStateFromError`, pero la biblioteca aporta `resetErrorBoundary` y `resetKeys` probados; por un kilobyte se queda |
| `zustand` | 5.1.0 | 472 | 0 archivos de `src/` ni de `talleres/`; solo el ejemplo `n3-03-carrito` | 6 nov 2026 | **Se elimina de una aplicación real** que no lo use: aquí ninguna pantalla lo importa (0 bytes en el cliente) y solo respalda la comparación del taller nivel-03/03. Debería estar en `devDependencies` |
| `clsx` | 2.1.1 | 325 | 3 archivos | 18 sep 2026 | **Se podría eliminar**: con tres usos basta una plantilla de cadena; se queda por legibilidad y porque cuesta 325 bytes |
| `next`, `react`, `react-dom`, `pg`, `server-only` | 16.4.6, 19.3.1, 19.3.1, 8.23.1, 0.0.1 | n/a (base de la aplicación o solo servidor) | | | **Se quedan**: son el entorno. `pg` y `server-only` no llegan al navegador |

Observación sobre actualizaciones (`npm outdated`): hay versiones mayores nuevas de `eslint` (10), `typescript` (7), `vitest` (5) y `@types/node` (26) que no se tomaron; todas son de desarrollo y se dejan para una migración propia, no dentro del taller.
`marked` tiene una menor nueva (18.2.1) sin cambios de API.
