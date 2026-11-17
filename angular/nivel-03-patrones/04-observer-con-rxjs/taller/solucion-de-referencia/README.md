# Solución de referencia: Observer con RxJS (n3-04)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Esta solución vive dentro de la aplicación compartida [`../../../../solucion-de-referencia-app/`](../../../../solucion-de-referencia-app/) (Angular 22 con todos los talleres de la ruta). Una sola instalación sirve para todas las sesiones.

## Dónde está el código

- Código de la solución: `angular/solucion-de-referencia-app/talleres/nivel-03-patrones/04-observer-con-rxjs/`
- Pantalla del taller en la aplicación: ruta `/n3-04/taller`

## Cómo ejecutarla

Desde `angular/solucion-de-referencia-app/` (Node 26 y npm 11; la primera vez `npm ci`):

```bash
cd angular/solucion-de-referencia-app
npm ci
npx ng serve app        # http://localhost:4200/n3-04/taller
```

Para ver el taller con su API simulada (la misma que usan las pruebas E2E):

```bash
npx ng build app && PUERTO=49200 RAIZ=dist/app/browser node servidor/servidor.mjs
# http://localhost:49200/n3-04/taller
```

## Sus pruebas

Pruebas unitarias y de componentes (`flujos.spec.ts`):

```bash
cd angular/solucion-de-referencia-app
npx ng test app --include '../talleres/nivel-03-patrones/04-observer-con-rxjs/**/*.spec.ts'
```

Prueba E2E en Chromium (Playwright; la primera vez `npx playwright install chromium`):

```bash
cd angular/solucion-de-referencia-app
npx ng build app
npx playwright test -g 'n3-04 taller'
```

Todo junto (compilaciones, pruebas, E2E, Nx, microfrontend, Lighthouse): `./verificar.sh` en la carpeta de la aplicación (tarda varios minutos).

## Análisis

Qué se decidió y por qué: [ANALISIS.md](ANALISIS.md).
