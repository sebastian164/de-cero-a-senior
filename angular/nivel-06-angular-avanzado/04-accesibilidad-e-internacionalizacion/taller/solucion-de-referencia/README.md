# Solución de referencia: Accesibilidad e i18n (n6-04)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Esta solución vive dentro de la aplicación compartida [`../../../../solucion-de-referencia-app/`](../../../../solucion-de-referencia-app/) (Angular 22 con todos los talleres de la ruta). Una sola instalación sirve para todas las sesiones.

## Dónde está el código

- Código de la solución: `angular/solucion-de-referencia-app/talleres/nivel-06-angular-avanzado/04-accesibilidad-e-internacionalizacion/`
- Pantalla del taller en la aplicación: ruta `/n6-04/taller`

## Cómo ejecutarla

Desde `angular/solucion-de-referencia-app/` (Node 26 y npm 11; la primera vez `npm ci`):

```bash
cd angular/solucion-de-referencia-app
npm ci
npx ng serve app        # http://localhost:4200/n6-04/taller
```

Para ver el taller con su API simulada (la misma que usan las pruebas E2E):

```bash
npx ng build app && PUERTO=49200 RAIZ=dist/app/browser node servidor/servidor.mjs
# http://localhost:49200/n6-04/taller
```

## Sus pruebas

Prueba E2E en Chromium (Playwright; la primera vez `npx playwright install chromium`):

```bash
cd angular/solucion-de-referencia-app
npx ng build n6-04-i18n
npx ng build app
npx playwright test talleres/nivel-06-angular-avanzado/04-accesibilidad-e-internacionalizacion/taller.e2e.ts
```

Comprobaciones adicionales (desde `angular/solucion-de-referencia-app/`):

```bash
bash talleres/nivel-06-angular-avanzado/04-accesibilidad-e-internacionalizacion/ci/probar-workflow.sh
```

- `bash talleres/nivel-06-angular-avanzado/04-accesibilidad-e-internacionalizacion/ci/probar-workflow.sh`: Ejecuta el workflow de accesibilidad en una copia limpia.

Todo junto (compilaciones, pruebas, E2E, Nx, microfrontend, Lighthouse): `./verificar.sh` en la carpeta de la aplicación (tarda varios minutos).

## Análisis

Qué se decidió y por qué: [ANALISIS.md](ANALISIS.md).
