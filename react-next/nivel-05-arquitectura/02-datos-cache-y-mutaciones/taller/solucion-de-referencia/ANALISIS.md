# Taller nivel-05/02: decisiones de caché y cómo se comprobaron

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-05-arquitectura/02-datos-cache-y-mutaciones/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Versión: Next.js 16.4.6 con `cacheComponents: true` (modelo de `'use cache'`). Todo se comprobó en producción
(`next build` y `next start`) con `e2e/n5-02.spec.ts`; las secuencias exactas quedan en `mediciones/n5-02-cache.json`.
Código: `src/app/(principal)/n5/02-cache/` y `src/origen-cache.ts` (un origen que se modifica por `POST /api/n5-02/origen` y cuenta lecturas).

## 1. Decisión por consulta

| Consulta | Estrategia | Justificación |
|---|---|---|
| Lista del catálogo (`listaPorTiempo`) | Por tiempo: `cacheLife({ stale: 1, revalidate: 3, expire: 60 })` | Mucho tráfico; tolera segundos de antigüedad; no se sabe cuándo cambia algo |
| Detalle de un artículo (`detallePorEtiqueta`) | Bajo demanda: `cacheTag('articulo-<id>')` + `cacheLife('max')` | Se sabe exactamente cuándo cambia (tras la mutación o un webhook) |
| Precio al pagar (`precioExacto`) | Sin caché (`connection()`, se lee en cada petición) | Debe ser exacto |
| Pedidos del usuario (`listarPedidosDe`) | Por usuario: el id va en los argumentos (clave) y en la etiqueta `pedidos-<id>` | Dato personal; se invalida al mutar |

## 2. Comprobación (modificar el origen y observar)

Lista por tiempo: inicio 32000 → el origen pasa a 40000 → lectura inmediata **32000** (guardado) → pasados 3,5 s la primera lectura ya da **40000**.
(Resultado observado: tras vencer, la primera petición no recibió el valor viejo; no se obtuvo *stale-while-revalidate* con este perfil corto.)

Detalle por etiqueta: inicio 9000 → origen 12000 → lectura **9000** (guardado) → `revalidateTag('articulo-2', 'max')` desde un manejador →
primera lectura **9000** (todavía el dato viejo mientras se regenera) → segunda lectura **12000**. Es el comportamiento *stale-while-revalidate* que
la nota de versión de la sesión describe.

Sin caché: 32000 → 55555 en la siguiente petición.

## 3 y 4. Mutación, invalidación e interfaz optimista

`/n5/02-cache/pedidos` usa la acción del README (`proyectos/react-next/n5-02-datos-cache/acciones.ts`, con los seis pasos y `updateTag`) y la lista
optimista del README. Se comprobó con Chromium: al cancelar, el pedido desaparece de inmediato; al recargar sigue sin aparecer aunque
`listarPedidosDe` esté guardado con `cacheLife('max')` (la acción expiró su etiqueta con `updateTag`);
al intentar cancelar un pedido de hace 3 días, la acción devuelve `{ ok: false }`, React revierte el estado optimista y aparece el aviso.

## 5. `cache` de React

`/n5/02-cache/usuario-actual`: tres componentes piden el usuario; una petición suma **1** consulta al contador (6 → 7). Con
`?modo=sin-cache` (lectura sin `cache`) la misma petición suma **3** (8, 9, 10).

Hallazgo de configuración: con `partialPrefetching: true` esa misma petición ejecutaba `buscarUsuario` dos veces (contador +2). Esta
aplicación usa `partialPrefetching: false`.

## 6. Fuga entre usuarios

`por-usuario?modo=mal`: la función guardada recibe al usuario por una vía que no es argumento, así que la clave de caché es la misma para todos. Ana entra primero y
Luis ve "Café de Huila, Chocolate" (los pedidos de Ana). Corrección (`modo=bien`): `pedidosBien(usuarioId)`: el usuario es argumento, por tanto parte de la clave, y se incluye en la etiqueta.
Luis ve "Prensa francesa". Las funciones con `'use cache'` no pueden leer `cookies()`: el usuario debe llegar como argumento.

## 7. Comportamiento por defecto en la versión usada

Con `cacheComponents: true` (Next.js 16.4.6) nada se guarda por defecto: lo que lee datos sin `'use cache'`, `cookies()`, `headers()`, `params` o
`searchParams` es **dinámico** y debe estar dentro de un `<Suspense>` (o tras `loading.tsx`); si no, `next build` falla con
"Next.js encountered uncached or runtime data during prerendering" (se vio varias veces al probar los ejemplos). Lo guardado se declara con `'use cache'` + `cacheLife` + `cacheTag`;
las rutas que solo usan datos guardados quedan como cáscara estática (`○` / `◐` en la salida de la construcción). Referencia: documentación de Next.js 16, "Caching" y "Cache Components"
(https://nextjs.org/docs/app/getting-started/caching). La documentación en línea no se consultó durante esta ejecución; lo anterior es lo observado ejecutando.

## 8. `updateTag` frente a `revalidateTag(etiqueta, 'max')`

Prueba: `e2e/n5-02.spec.ts`, "contraste: updateTag…". El detalle del artículo 1 (`cacheTag('articulo-1')`, `cacheLife('max')`) se lee, el origen pasa de 32000 a 7777 y
se pulsa "Invalidar con updateTag" (acción de servidor en `catalogo/[id]/acciones.ts`). Secuencias reales en `mediciones/n5-02-cache.json`:

| Invalidación | Quién lee | Valor en la primera lectura |
|---|---|---|
| `revalidateTag('articulo-2', 'max')` desde un manejador | la petición siguiente | **9000**, el dato viejo (se afirma `s[2]`); la segunda lectura da 12000 |
| `updateTag('articulo-1')` desde una acción | la respuesta de la propia acción | **7777**, el dato nuevo, sin pasar por el viejo |

Hallazgo (observado y afirmado en la prueba como comportamiento de 16.4.6: si una versión nueva lo corrige, la prueba lo delata): en esta ruta, prerenderizada por `generateStaticParams`, las peticiones nuevas posteriores a `updateTag`
(otro cliente por `request`, la recarga del mismo navegador y una pestaña nueva) siguieron recibiendo **32000** durante los 9 s que se vigiló. Es decir, en Next.js 16.4.6
`updateTag` garantiza que quien muta vea su cambio, pero no refrescó la copia estática de la ruta para los demás. En la lista de pedidos (ruta dinámica por
usuario) sí se vio el cambio al recargar. Para una página estática que otros también leen, la invalidación bajo demanda que funcionó fue `revalidateTag(etiqueta, 'max')`.

El webhook `/api/n5-02/revalidar` ahora exige la cabecera `x-secreto-webhook` (`SECRETO_WEBHOOK`); sin ella responde 401 (comprobado en la misma prueba).
