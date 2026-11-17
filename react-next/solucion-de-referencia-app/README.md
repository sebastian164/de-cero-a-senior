# Aplicación de referencia de React, Next.js, TypeScript y frontend senior

Una sola aplicación Next.js 16 que contiene las soluciones de los talleres de tres rutas: `react-next` (23 talleres), `typescript` (3) y `frontend-senior` (3). Cada taller tiene en su sesión una carpeta `taller/solucion-de-referencia/` con un `README.md` (cómo ejecutarlo) y un `ANALISIS.md` (qué se midió y por qué se decidió así); el código vive aquí.

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

## Dónde está cada cosa

| Carpeta | Qué contiene |
|---|---|
| `talleres/<ruta de la sesión>/` | La solución de cada taller y su `taller.test.ts(x)`. La ruta repite la de la sesión (por ejemplo `talleres/react-next/nivel-02-solid/01-srp/`). |
| `src/` | Las pantallas de Next.js (`src/app/`) y los módulos de cliente que usan los talleres de los niveles 5 y 6 y `frontend-senior`. |
| `proyectos/` | Los ejemplos "Después" de los README del curso, ya como código que compila, y la estructura por funcionalidades de nivel-05/03. |
| `e2e/` | Pruebas de Playwright que abren el navegador contra `next start`. |
| `scripts/` | Comprobaciones auxiliares (casos que deben fallar al compilar, mutación, compilador de React, réplicas en Docker, presupuesto de JavaScript, Lighthouse). |

Versiones fijadas en `package.json` y `package-lock.json`: Node 26, Next.js 16.4.6, React 19.3.1, TypeScript 5.9.3, Vitest 4.1.12, Playwright 1.65.1.

## Preparación (una vez)

```bash
cd react-next/solucion-de-referencia-app
npm ci
npx playwright install chromium webkit   # solo si vas a ejecutar las pruebas de navegador
docker compose -p ref-react-next up -d --wait   # PostgreSQL 17 en el puerto 49540; solo lo usa nivel-05/03
```

Para bajar la base de datos al terminar: `docker compose -p ref-react-next down -v`.

## Pruebas

| Qué | Comando | Tarda |
|---|---|---|
| Tipos de todo el código | `npm run typecheck` | segundos |
| Pruebas unitarias y de componentes de todos los talleres (Vitest) | `npm test` | unos 10 s |
| Pruebas de un taller | `npx vitest run talleres/<ruta de la sesión>` | segundos |
| Lint y formato | `npm run lint` y `npm run formato` | segundos |
| Construcción de producción con presupuesto de JavaScript | `npm run build` | 1 a 2 min |
| Pruebas de navegador de un taller (necesitan `npm run build` antes) | `npx playwright test e2e/<archivo>.spec.ts --project=chromium` | segundos o minutos |
| Todas las pruebas de navegador (Chromium y WebKit) | `npx playwright test` | unos 5 min |

Playwright arranca solo `next start` en el puerto 49510 (se cambia con `PUERTO_APP`). Las pruebas dejan capturas en `capturas/` y cifras en `mediciones/`; ambas carpetas se generan y no se publican.

Cada taller indica en su `README.md` el comando exacto de sus pruebas.
