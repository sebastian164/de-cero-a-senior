# Taller nivel-03/05: Suspense y límites de error. Decisiones y mediciones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-03-patrones/05-suspense-limites-de-error-y-patrones-heredados/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.tsx`; `e2e/n3-05.spec.ts` (CLS en Chromium); pantallas `/n3/05-suspense` y `/n3/05-suspense/cls`.

## Punto 1. Tres secciones de distinta velocidad: dónde van los límites

Resumen (80 ms), movimientos (600 ms) y recomendados (1,5 s). Agrupación elegida: **dos** límites, `<Suspense>{Resumen}{Movimientos}</Suspense>` y `<Suspense>{Recomendados}</Suspense>`.
Resumen y movimientos se leen juntos (el saldo sin sus movimientos no dice nada) y la diferencia entre 80 y 600 ms haría que el saldo apareciera solo y luego se moviera la página al llegar la lista; se entregan juntos a los 600 ms.
Recomendados es lo menos importante y lo más lento: tiene su límite aparte para no retener lo importante 900 ms más. Un límite por sección (tres) provocaría más saltos y destellos; un solo límite (uno) esperaría 1,5 s por todo.

## Punto 2. Esqueleto frente a indicador giratorio: CLS medido

`/n3/05-suspense/cls`: un bloque de 320 px llega a los 700 ms; debajo hay un párrafo. CLS acumulado (PerformanceObserver `layout-shift`, Chromium, `next start`, `mediciones/n3-05.json`):

| Fallback | CLS |
|---|---|
| giratorio de 24 px (no reserva el espacio) | **0,0192**: el párrafo baja 296 px al llegar el bloque |
| esqueleto de 320 px (reserva el alto real) | **0** |

El valor del giratorio es pequeño en términos absolutos (solo se mueve un párrafo, y CLS pondera por la fracción de pantalla afectada); en una página con más contenido debajo, el mismo salto de 296 px pesaría mucho más. La prueba exige giratorio mayor que 0,01 y esqueleto exactamente 0.

## Punto 3. Límites de error por sección

Cada sección tiene su `<Seccion>` con alternativa, botón de reintento y reporte. Con recomendaciones caídas: aparece "No pudimos cargar recomendaciones.", el inventario ("42 unidades") sigue visible, se reporta "Servicio de recomendaciones caído" y, al reparar y pulsar "Reintentar", vuelve "Café de Huila". El bloque del README (`ErrorBoundary` + `Suspense`) reporta una vez y se recupera con el reintento.

## Punto 4. Un error en un manejador de eventos

El límite **no lo atrapa**: la prueba pulsa "Guardar sin manejo", la alternativa nunca aparece y el error sale por `window.onerror` (1 evento, "Fallo dentro del manejador"). Manejado donde ocurre (`try/catch` en el manejador): aparece "No se pudo guardar. Intenta de nuevo." y se reporta `timeout`. Los límites cubren errores de renderizado, no de eventos ni de código asíncrono.

## Punto 5. HOC y propiedad de renderizado a hooks

La versión con propiedad de renderizado y con hook dan el mismo resultado; el HOC de registro y su hook, y `conSesion` del README frente a `useSesion`, protegen igual. Los hooks evitan el anidado de envolturas y los problemas de tipos del HOC.

## Punto 6. Errores de una pantalla, esperados e inesperados

| Error | Clase | Qué se hace |
|---|---|---|
| Campo inválido al enviar un formulario | esperado | se muestra en su campo; no se reporta al colector |
| El servidor responde 404 por un recurso inexistente | esperado | `not-found` / mensaje; no es un fallo |
| Pedido que ya no se puede cancelar (regla de negocio) | esperado | mensaje al usuario; el colector lo ignora (`esperado: true`; comprobado en `e2e/fe-02.spec.ts`) |
| Recomendaciones caídas / red / 5xx | inesperado | límite por sección con reintento y **reporte** con versión y ruta |
| Excepción en un manejador (`Fallo dentro del manejador`) | inesperado | `try/catch` donde ocurre: mensaje y **reporte** |
| Excepción de renderizado | inesperado | límite de error de la sección; el resto de la página sigue |
