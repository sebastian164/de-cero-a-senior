# Taller nivel-05/04: renderizado y despliegue

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Esta solución no tiene archivos propios bajo `talleres/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Next.js 16.4.6, `cacheComponents: true`, `output: 'standalone'`. Código en `src/app/(principal)/n5/04-renderizado/` (doce pantallas), `src/app/sitemap.ts` y `src/app/robots.ts`.
Comprobaciones: `scripts/comprobar-estrategias.py` (lee la salida de `next build`), `e2e/n5-04.spec.ts` y `scripts/replicas.sh` (Docker). Cifras exactas: `mediciones/n5-04.json` y `mediciones/n5-04-replicas.json`.

## 1 y 2. Tabla de estrategias, implementada y revisada en la salida de la construcción

| Página (ruta bajo `/n5/04-renderizado`) | Estrategia | Cómo se implementó | Salida de `next build` |
|---|---|---|---|
| `inicio` | Estática | Sin datos ni petición | `○` |
| `categoria/[slug]` | Estática con regeneración por tiempo | `generateStaticParams` + `'use cache'` con `cacheLife({ revalidate: 60 })` | `○` Revalidate 1m |
| `producto/[id]` | Estática con regeneración bajo demanda | `generateStaticParams` + `cacheTag('producto-<id>')` + `cacheLife('max')` | `○` Revalidate 30d |
| `carrito` | Cáscara estática con hueco dinámico | `<Suspense>` alrededor de la lectura de `cookies()` | `◐` |
| `busqueda?q=` | Dinámica con transmisión | `searchParams` + `<Suspense>` con resultados lentos | `◐` |
| `mi-cuenta` | Dinámica, sin indexar | cookie de sesión + `robots: { index: false }` | `◐` |
| `lenta/bloqueante` | Dinámica sin transmisión | `export const instant = false` | `ƒ` |

El script falla si alguna ruta sale distinta de lo esperado. Con Cache Components, leer datos de la petición fuera de un `Suspense` hace fallar `next build`
(lo vimos con `searchParams` en varias páginas): la estrategia queda declarada o la construcción no pasa.

## 3. Transmisión (tiempo hasta ver contenido útil, Chromium sobre `next start`, sección lenta de 1,5 s)

| Variante | Contenido útil visible |
|---|---|
| Sin transmisión (`instant = false`) | 1800 ms |
| Una sola frontera `Suspense` que envuelve todo | 1801 ms |
| Frontera solo alrededor de la sección lenta | 36 ms |

## 4. Metadatos, `sitemap` y `robots`
`generateMetadata` da título, descripción y `canonical` por producto; `/sitemap.xml` sale de los datos (fichas + páginas fijas); `/robots.txt` desautoriza `mi-cuenta` y `/api/`;
`mi-cuenta` lleva `noindex`. Un producto inexistente responde **404** (no 200). Todo en `e2e/n5-04.spec.ts`.

## 5. Contenedor con salida independiente y dos réplicas
`Dockerfile` (a partir de `.next/standalone`, imagen `node:26-slim`) y `scripts/replicas.sh`: dos contenedores (A y B) de la misma imagen. Ambos sirven al inicio el mismo valor guardado (`hora-cacheada`).
Se invalida la etiqueta `hora` en A: A pasa a un valor nuevo, **B sigue sirviendo el viejo**. Cada réplica tiene su caché local y la invalidación solo llega a la que la recibe.
En producción real con varias réplicas hace falta un manejador de caché compartido (o invalidar en todas) tal como dice el README. Los números exactos están en `mediciones/n5-04-replicas.json`.

## 6. `NEXT_PUBLIC_`
`/config-publica` lee `NEXT_PUBLIC_URL_API` en un componente de cliente: el valor (`https://api.tienda.example`) aparece dentro de los archivos JavaScript descargados por el navegador
(`publicaEnJavaScript: true`) y también en el HTML. La variable de servidor `SECRETO_DEL_SERVIDOR` no aparece ni en el HTML ni en el JavaScript descargado. Se fija al construir: cambiarla exige reconstruir.

## 7. Métricas con y sin `next/image` y `next/font` (red de 1,6 Mbps y 150 ms, imagen PNG de 1600×900)

| | LCP | Bytes de imagen | Bytes de fuente | CLS |
|---|---|---|---|---|
| `<img>` sin dimensiones y `@font-face` | 21456 ms | 4 193 298 | 57 730 | 0 |
| `next/image` (con `priority`) y `next/font/local` | 1904 ms | 127 462 | 28 874 | 0 |

El CLS salió 0 en ambas variantes en esta medición, así que no se puede afirmar una mejora de CLS con estos datos; la diferencia medida está en el peso y en el LCP.
La fuente de las dos variantes es el mismo archivo (Geist, latín); lo que cambia es cómo se carga.
