# Solución de referencia: Componentes con señales (n1-03)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Esta solución vive dentro de la aplicación compartida [`../../../../solucion-de-referencia-app/`](../../../../solucion-de-referencia-app/) (Angular 22 con todos los talleres de la ruta). Una sola instalación sirve para todas las sesiones.

## Dónde está el código

- Código de la solución: `angular/solucion-de-referencia-app/talleres/nivel-01-fundamentos/03-componentes-con-senales/`
- Pantalla del taller en la aplicación: ruta `/n1-03/taller`

## Cómo ejecutarla

Desde `angular/solucion-de-referencia-app/` (Node 26 y npm 11; la primera vez `npm ci`):

```bash
cd angular/solucion-de-referencia-app
npm ci
npx ng serve app        # http://localhost:4200/n1-03/taller
```

Para ver el taller con su API simulada (la misma que usan las pruebas E2E):

```bash
npx ng build app && PUERTO=49200 RAIZ=dist/app/browser node servidor/servidor.mjs
# http://localhost:49200/n1-03/taller
```

## Sus pruebas

Pruebas unitarias y de componentes (`taller.spec.ts`):

```bash
cd angular/solucion-de-referencia-app
npx ng test app --include '../talleres/nivel-01-fundamentos/03-componentes-con-senales/**/*.spec.ts'
```

Prueba E2E en Chromium (Playwright; la primera vez `npx playwright install chromium`):

```bash
cd angular/solucion-de-referencia-app
npx ng build app
npx playwright test -g 'n1-03 taller'
```

Comprobaciones adicionales (desde `angular/solucion-de-referencia-app/`):

```bash
bash talleres/nivel-01-fundamentos/03-componentes-con-senales/migracion/ejecutar-migracion.sh
```

- `bash talleres/nivel-01-fundamentos/03-componentes-con-senales/migracion/ejecutar-migracion.sh`: Aplica los esquemas oficiales de migración a señales y compara con el resultado esperado.

Todo junto (compilaciones, pruebas, E2E, Nx, microfrontend, Lighthouse): `./verificar.sh` en la carpeta de la aplicación (tarda varios minutos).

## Análisis

Qué se decidió y por qué: [ANALISIS.md](ANALISIS.md).
