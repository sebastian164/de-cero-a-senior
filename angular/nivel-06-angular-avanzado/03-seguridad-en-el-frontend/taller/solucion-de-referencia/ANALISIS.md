# Taller n6-03 — Seguridad en el frontend

Pantallas: `/n6-03/taller/*`. API simulada: `servidor/apis/n6-03.mjs`. Pruebas: `taller.e2e.ts` (5), `buscar-secretos.mjs`, `npm audit`.

| # | Solución | Resultado |
|---|---|---|
| 1 | `XssPeligroso` (`bypassSecurityTrustHtml` con `<img src=x onerror=...>`) frente a `XssSaneado` (`[innerHTML]` sin bypass) y `XssSeguro` (`{{ texto }}`) | El comentario malicioso **ejecutó** el script en la primera (`window.__xss > 0`). En la segunda Angular quitó el atributo `onerror` (y la imagen queda inerte); en la tercera se ve el HTML como texto. Corrección recomendada: la tercera |
| 2 | `soloAdmin` lee `localStorage.rol`. Con las herramientas del navegador (`localStorage.setItem('rol','admin')`) se entra a `/n6-03/taller/admin`; la API `GET /secreto` responde **403** sin el token que emite el servidor y la pantalla muestra "El servidor rechazó la petición (403)". Con sesión real (`POST /sesion`) responde 200 | La guarda es experiencia de usuario; solo el servidor protege el dato |
| 3 | `npm audit`: 7 hallazgos **moderados** en dependencias de desarrollo (`typescript-eslint`, `minimatch`), 0 graves y 0 en producción. `npm audit fix` los resolvió: 0 vulnerabilidades | Sin hallazgos graves que resolver |
| 4 | `node buscar-secretos.mjs` sobre `dist/app/browser`: 85 archivos, 0 hallazgos, 0 mapas de código. `--autoprueba` planta una `sk_live_…` en una copia y comprueba que el detector la encuentra | OK |
| 5 | CSP: `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` | Ver abajo |
| 6 | `SESION.md` | |

## CSP: el ajuste que hizo falta

Con la política anterior, **todas** las pantallas daban una violación `script-src-elem inline`: el compilador de producción de Angular inserta el CSS crítico con un `<script>` en línea (`media="print" … onload` → un script que cambia el `media`). Se ajustó con `optimization.styles.inlineCritical: false` en `angular.json`. Resultado: las 54 pantallas (ejemplo y taller de las 28 sesiones, salvo la del XSS) cargan con la CSP **sin una sola violación**, y la CSP **bloquea** el controlador `onerror` del comentario malicioso aun con `bypassSecurityTrustHtml` (la violación `script-src` se registra y `window.__xss` queda en 0).

Pendiente de endurecer: `style-src 'unsafe-inline'` sigue porque los estilos de componente se insertan en línea; la alternativa es un *nonce* por respuesta (`ngCspNonce`), que exige un servidor que lo genere en cada petición (el servidor de esta aplicación sirve archivos estáticos y no lo hace).

## Comprobación: por qué la CSP exige `inlineCritical: false`

Se reconstruyó `app` una vez con `optimization.styles.inlineCritical: true` (el valor por omisión de producción) y se sirvió con la política del punto 5 (`css-critico-con-csp.mjs`, que espera el build en `tmp/app-critico`). El `index.html` generado contiene un **`<script>` en línea**:

```
<script>document.querySelectorAll('link[data-beasties-media]').forEach(function(l){l.media=l.getAttribute('data-beasties-media')...
```

y el navegador lo bloquea con: `Executing inline script violates the following Content Security Policy directive 'script-src 'self''. Either the 'unsafe-inline' keyword, a hash ('sha256-...'), or a nonce ('nonce-...') is required`. Es decir, lo que redacta el README (el compilador inserta un `<script>` en línea que la política bloquea) es lo observado en Angular 22.2.6; no es un manejador `onload` en un `<link>`. Con `inlineCritical: false` no hay script en línea y no hay violaciones.
