# Taller n6-02 — SSR, hidratación y SEO

Aplicación: `proyectos/ssr` (proyecto `n6-02-ssr`, Angular 22.2.6, `@angular/ssr` 22.2.x, Express 5). Construida con `ng build n6-02-ssr` (producción y desarrollo) y servida por su `server.mjs`. Pruebas: `taller.e2e.ts` (9 pruebas).

| # | Solución | Prueba |
|---|---|---|
| 1 | SSR sobre una aplicación con partes pública (`/`, `/producto/:id`) y privada (`/privado`) | todas |
| 2 | `app.routes.server.ts`: `''` → `Prerender`, `producto/:id` → `Server`, `privado` → `Client`, `**` → `Server` | "puntos 1 y 2": el HTML del inicio y del detalle trae el contenido; el de `/privado` solo la cáscara |
| 3 | `Descuadre` (tabla sin `tbody`) provoca `NG0500: During hydration Angular expected <tr> but found <tbody>`; `Cuadre` (con `tbody` y lo dependiente del navegador tras `afterNextRender`) no | "punto 3", con el build de desarrollo (los errores de hidratación solo se registran en desarrollo) |
| 4 | `AlmacenLocal` (abstracción con memoria en el servidor), `afterNextRender` para `window.innerWidth` | "punto 4": el servidor no escribe `ReferenceError` y el navegador guarda y lee `localStorage` |
| 5 | `provideClientHydration()` incluye la caché de transferencia | "punto 5": la API recibe **1** petición y el navegador **0** |
| 6 | `Seo` (título, descripción, `og:*` y JSON-LD `Product`) | "punto 6" con `javaScriptEnabled: false` |
| 7 | LCP con CPU 4× más lenta, latencia 150 ms y 1,6 Mb/s, 3 repeticiones | "punto 7" |

## Hallazgos

- **Un desajuste de fecha NO produce error de hidratación.** El servidor pintó `02:21:06.822` y el navegador `02:21:04.692`: Angular reutiliza el nodo de texto y lo reescribe sin avisar. Lo que lanza `NG0500` es la **estructura** del DOM que no coincide (HTML inválido que el navegador repara, manipulación directa del DOM). El README decía "fechas… hay errores y parpadeos": corregido.
- `@if (esNavegador)` con `isPlatformBrowser` tampoco lanza `NG0500` (el navegador crea la vista que el servidor no tenía), pero es contenido que aparece de golpe: mejor `afterNextRender`.
- `security.allowedHosts` queda vacío en un proyecto nuevo con SSR y el servidor responde **400** a cualquier petición hasta declarar los hosts (`localhost` aquí).
- **LCP** (mediana de 3): con SSR **196 ms**, sin SSR (modo `Client` de la misma pantalla) **1948 ms**. Los datos quedan en `tmp/medidas-n6-02.json`.
- **Estado compartido**: una variable de módulo se compartió entre tres peticiones (1, 2, 3); un servicio `providedIn: 'root'` valió 1 en las tres: se crea de nuevo en cada petición. El riesgo del README es real para variables de módulo, cachés propias y singletons que no pasan por la inyección de Angular.
- **404 real**: el producto inexistente y una ruta desconocida responden 404 (`RESPONSE_INIT`); el producto existente, 200.
- **Qué pasa en el DOM tras NG0500** (medido con un `MutationObserver` en Chromium, en producción y en desarrollo): ni en producción ni en desarrollo se elimina ningún nodo (0 removidos), es decir, Angular no recrea el DOM. En desarrollo hay un error `NG0500` en la consola y el párrafo que solo dibuja el navegador no aparece; en producción no hay ningún aviso. El README ya no dice "recrea el DOM, con parpadeo".
