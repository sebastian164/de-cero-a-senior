# Solución de referencia: aplicación de los talleres de Angular

> **Intenta cada taller antes de abrir esto:** ver la respuesta primero convierte el taller en lectura.

Aplicación Angular 22 que reúne las soluciones de los 28 talleres de `angular/`, el código "después" de los ejemplos de cada README y los proyectos auxiliares (SSR, Nx, microfrontend, OpenAPI, Lighthouse). Se publica una sola vez; cada sesión tiene en `taller/solucion-de-referencia/` un README con la ruta exacta de su código y los comandos para probarlo, y su `ANALISIS.md`.

## Requisitos

Node 26, npm 11 y Chromium de Playwright (`npx playwright install chromium`). Para la comprobación de la sesión n6-04 también `ruby` y `rsync`. Sin Docker ni clústeres. Hace falta red solo para `npm ci`.

```bash
cd angular/solucion-de-referencia-app
npm ci
```

## Ejecutar

| Qué | Orden |
|---|---|
| Servir la aplicación en desarrollo | `npx ng serve app` (rutas `/<sesión>/ejemplo` y `/<sesión>/taller`, por ejemplo `/n1-04/taller`) |
| Pruebas unitarias y de componentes de todas las sesiones | `npx ng test app` |
| Las de una sesión | `npx ng test app --include '../talleres/<nivel>/<sesión>/**/*.spec.ts'` |
| Pantallas y E2E (Playwright, Chromium) | `npx ng build app && npx playwright test` |
| Servir lo construido con la API simulada | `npx ng build app && PUERTO=49200 RAIZ=dist/app/browser node servidor/servidor.mjs` |
| Todo (compilaciones, pruebas, E2E, medidas, Nx, microfrontend, Lighthouse) | `./verificar.sh` (varios minutos; instala las dependencias de `proyectos/*` si faltan; registro en `tmp/verificar.log`) |

`./verificar.sh` usa los puertos 49200-49299 y termina con código 0 solo si todo pasa.

## Carpetas

- `src/`: la aplicación; `servidor/`: servidor de Node con las API simuladas de cada sesión.
- `talleres/<nivel>/<sesión>/`: la solución de cada taller y sus pruebas.
- `proyectos/`: `ejemplos/` (el código "después" de los README), `ssr/`, `mfe/`, `nx-tienda/`, `openapi/`, `lighthouse/`, `compilacion-negativa/`. Los cuatro con `package.json` propio se instalan con `npm ci` dentro de su carpeta.
- `e2e/`: pruebas de Playwright de las pantallas y los flujos.
- Las salidas (`dist/`, `tmp/`, `capturas/`, `resultados-e2e/`) se generan al ejecutar y no se publican.
