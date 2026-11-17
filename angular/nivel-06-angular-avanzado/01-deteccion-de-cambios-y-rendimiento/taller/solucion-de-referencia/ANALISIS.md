# Taller n6-01 — Detección de cambios y rendimiento

Aplicación medida: esta misma aplicación (`ng build app`, producción, Angular 22.2.6). Pantallas del taller: `/n6-01/taller/*`. Mediciones guardadas en `tmp/medidas-n6-01.json` y `tmp/lighthouse.json`.

| # | Solución | Cómo se ejecuta |
|---|---|---|
| 1 | Lighthouse 12.8 en perfil móvil (el predeterminado), Chrome for Testing de Playwright | `proyectos/lighthouse/medir.mjs` |
| 2 | Análisis del paquete con el metafile de esbuild (`ng build --stats-json`) | `analizar-paquete.mjs` |
| 3 | Casos `Eager` → `OnPush` en parejas, con su corrección | `onpush.spec.ts` (2 pruebas) |
| 4 | 10.000 filas en el DOM frente a `cdk-virtual-scroll-viewport` | `taller.e2e.ts` (punto 4) |
| 5 | `@defer` para la gráfica (`on viewport`) y el mapa (`on interaction`) | `taller.e2e.ts` (punto 5) |
| 6 | Aplicación con Zone.js a propósito, la misma sin zona, y la versión corregida con señales | `taller.e2e.ts` (punto 6), proyectos `n6-01-zona` y `n6-01-sin-zona` |

## 1. Lighthouse (móvil, rendimiento)

| Ruta | Puntuación | LCP | CLS | TBT | FCP |
|---|---|---|---|---|---|
| `/n6-01/taller/filas-virtuales` | 87 | 3238 ms | 0 | 28 ms | 2938 ms |
| `/n6-01/taller/sin-defer` | 74 | 5628 ms | 0 | 25 ms | 2814 ms |

**INP.** Lighthouse no lo calcula en una carga sin interacciones, así que se midió aparte, en laboratorio, con una interacción real: `taller.e2e.ts › punto 1` hace un clic de Playwright con la CPU 4 veces más lenta y lee las entradas `event` de un `PerformanceObserver` (la API de la biblioteca `web-vitals`); la duración incluye el tiempo hasta el siguiente pintado. Mediana de 5 cargas del clic más lento:

| Interacción | INP de laboratorio (mediana) | Muestras (ms) |
|---|---|---|
| Clic en "10.000 filas" (lista plana) | **384 ms** (necesita mejora: 200-500 ms; más de 500 ms es pobre) | 344, 360, 384, 512, 552 |
| Clic en "Desplazamiento virtual" | **64 ms** (bueno: ≤ 200 ms) | 64, 64, 64, 72, 88 |

Es una medida de laboratorio (una interacción por carga, CPU limitada por software), no el INP de campo de usuarios reales, que es el percentil 75 de todas sus interacciones. TBT queda como complemento (bajo en ambas pantallas). El LCP de `sin-defer` es peor porque descarga 450 KB de datos de los dos componentes pesados antes de pintar.

## 2. Las tres dependencias más pesadas

`node analizar-paquete.mjs` (bytes que aporta cada paquete a la salida, todas las rutas): `@angular/core` 208 KB, `@angular/forms` 107 KB, `@angular/router` 96 KB (luego `@angular/common` 63 KB, `rxjs` 34 KB, `@angular/cdk` 31 KB). El código propio pesa unos 900 KB, de los cuales unos 450 KB son los datos sintéticos de los dos componentes pesados del punto 5. Conclusión: en una aplicación de este tamaño el marco domina las dependencias; ninguna biblioteca de terceros fuera del ecosistema Angular y RxJS entra en el paquete.

## 3. `Eager` → `OnPush`: qué deja de actualizarse

En Angular 22 un componente sin `changeDetection` ya es `OnPush`; los de `onpush.ts` declaran la estrategia de forma explícita para ver la pareja. Se comprobó en `onpush.spec.ts`:

| Caso | `Eager` | `OnPush` | Corrección |
|---|---|---|---|
| Objeto recibido por entrada y modificado en el sitio (`carrito.items.push`) | Lo pinta (se revisa con el anfitrión) | **No lo pinta**: la referencia no cambió | Crear un objeto nuevo (`signal.set({ items: [...] })`) |
| Campo normal cambiado fuera de la plantilla (temporizador, biblioteca) | Se pinta solo si algo revisa al anfitrión | **No se pinta**: nadie marcó el componente | Pasarlo a `signal`: la señal marca el componente |

Dato que se comprobó y que el README no dice: `Eager` en un proyecto **sin zona** tampoco se actualiza solo; solo se pinta cuando otra cosa (una señal del anfitrión) provoca una revisión. Por eso "funcionaba" por casualidad.

## 4. 10.000 filas

| | Nodos en el DOM | Tiempo desde el clic hasta pintar (2 fotogramas) |
|---|---|---|
| Lista plana (`@for` con `track f.id`) | 10.000 | 55-62 ms |
| `cdk-virtual-scroll-viewport` | 18 | 21-22 ms |

La lista virtual sigue dando acceso a las 10.000 filas (la prueba salta a la fila 9.995 y la ve). El tiempo plano es modesto porque la fila es mínima; crece con la complejidad de cada fila, el número de nodos es lo que no depende de ella.

## 5. `@defer`

Bytes de JavaScript descargados al entrar a cada ruta (sin caché): **sin `@defer` 793.658 B; con `@defer` 586.192 B** (−26 %), contando la gráfica, que entra por `on viewport`. El mapa (`on interaction`) se descarga solo al pulsar "Mostrar mapa" (2 archivos nuevos). Condiciones para que `@defer` sirva: cada componente diferido en **su propio archivo** y la ruta cargada con `loadComponent`; si otro componente importa el mismo archivo de forma estática, el código vuelve al paquete (se comprobó: con las dos clases en un solo archivo el mapa se descargaba junto con la gráfica, y con las rutas importadas de forma estática las 450 KB volvían al paquete de rutas).

## 6. Con zona y sin zona

`escenario.component.ts` tiene tres actualizaciones que dependen de Zone.js (`setTimeout`, una `fetch().then` y un `document.addEventListener`), con campos normales y la estrategia `Eager`, junto a la versión con señales.

| Aplicación | Campos normales | Señales |
|---|---|---|
| Con `zone.js` y `provideZoneChangeDetection()` | Los tres se actualizan | Los tres |
| Sin zona (`provideZonelessChangeDetection()`, sin `zone.js`) | **Los tres se quedan en "esperando"** | Los tres |

Qué se rompió y por qué: la zona intercepta temporizadores, promesas y oyentes y avisa a Angular tras cada uno; sin ella nadie avisa. Hallazgo extra: con la zona, el componente raíz también tiene que ser `Eager`; si es `OnPush`, la revisión no baja a los hijos aunque la zona la dispare (falló la primera versión por eso).
