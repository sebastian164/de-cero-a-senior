# Taller nivel-01/03: fundamentos de Next.js. Mediciones y decisiones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-01-fundamentos/03-fundamentos-de-next/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Next.js 16.4.6, `next build` y `next start`, Chromium con Playwright. Pruebas: `e2e/n1-03.spec.ts`; datos: `mediciones/n1-03.json`.
Pantallas: `/n1/03-next` (inicio), `/n1/03-next/libros` (lista), `/n1/03-next/libros/[id]` (detalle), `/n1/03-next/libros-cliente` (la lista toda en el cliente).

## Punto 3. loading.tsx, error.tsx y not-found.tsx provocados

| Caso | Cómo se provoca | Qué se comprueba |
|---|---|---|
| `loading.tsx` | navegar de la lista al detalle (la lectura tarda 400 ms a propósito) | "Cargando libro…" visible y luego el título |
| `error.tsx` | `/libros/1?fallar=1` | "No pudimos mostrar el libro" y el botón "Reintentar"; el error sí llega a la consola |
| `not-found.tsx` | `/libros/999` | "Ese libro no existe" |

**Hallazgo: el código de estado de `not-found` es 200, no 404.** Como el detalle tiene `loading.tsx`, la página se envía en streaming: la cáscara sale con 200 antes de que `notFound()` se ejecute y el
estado HTTP ya no puede cambiar. Next compensa con `<meta name="robots" content="noindex"/>` y el marcador `NEXT_HTTP_ERROR_FALLBACK;404` en la carga (la prueba los comprueba). Se intentó decidir el 404 antes de la frontera de carga
con un `layout.tsx` que consulte si el libro existe, y con `cacheComponents: true` la compilación lo rechaza ("uncached or runtime data during prerendering"): leer `params` fuera de `<Suspense>` no está permitido.
Quien necesite un 404 real para buscadores debe renunciar a `loading.tsx` en esa ruta o resolver la existencia en un proxy/middleware.

## Punto 4. Crear un libro y revalidar la lista

`FormularioLibro` usa `useActionState` con la acción `crearLibro` (valida con Zod y llama a `revalidatePath('/n1/03-next/libros')`). Pruebas: con JavaScript, el libro nuevo aparece en la lista y se muestra "Creado: …";
**sin JavaScript** (contexto con `javaScriptEnabled: false`) el formulario se envía de forma nativa y la lista ya trae el libro nuevo al recargar.

## Punto 5. Cuánto JavaScript se envía

Las dos páginas son la misma: lista de libros con su resumen en Markdown (formateado con `marked`) y el formulario para crear uno. En `/libros` la lista se obtiene y se formatea en el servidor; en `/libros-cliente` toda la página lleva `'use client'`,
los datos se piden desde el navegador (`/api/n1-03/libros`) y `marked` se ejecuta allí. Bytes sin comprimir de los `<script>` cargados (Chromium, `waitUntil: 'networkidle'`; `mediciones/n1-03.json`):

| Página | Archivos | Bytes totales | Bytes solo de esta página |
|---|---|---|---|
| `/libros` (servidor, solo el formulario es cliente) | 11 | 496 859 | 1 621 |
| `/libros-cliente` (`'use client'` en toda la página) | 11 | 546 499 | 51 261 |

La prueba afirma la dirección: lo propio de la página toda-cliente (51 261) es mayor que lo de la de servidor (1 621), unos 49,6 KB más: casi todo es `marked`, que en la de servidor se queda en el servidor. Los 496 KB restantes son el runtime de Next y React y los
proveedores del diseño raíz, comunes a ambas. Sin una biblioteca de por medio la diferencia sería de menos de 1 KB (medición anterior con solo seis `<li>`: 1 621 frente a 802 bytes, sin `marked` ni formulario en la versión cliente, es decir, no comparable).
El resto de lo que cambia al marcar todo como cliente:

| | Servidor | Todo cliente |
|---|---|---|
| ¿Los libros están en el HTML inicial? | **sí** (la prueba lo comprueba) | no: el HTML trae "Cargando…" |
| Peticiones para ver los libros | 1 (el documento) | 2 (documento y `/api/n1-03/libros`, ya hidratado) |
| Biblioteca de Markdown (`marked`) | queda en el servidor | viaja al navegador (unos 50 KB sin comprimir) |

## Punto 6. La frontera entre servidor y cliente

La frontera está en `formulario-libro.tsx`, la única pieza con `'use client'`: necesita `useActionState` (estado de la acción pendiente y errores). La lista (`listarLibros`, que lee `libros.json` con `server-only`) y el detalle son componentes de servidor:
no tienen estado ni eventos, y así el HTML llega con los datos y sin JavaScript propio. Se pone la frontera lo más abajo posible: el formulario, no la página.
