# Solución de referencia: Monorepos y microfrontends (n5-05)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Esta solución vive dentro de la aplicación compartida [`../../../../solucion-de-referencia-app/`](../../../../solucion-de-referencia-app/) (Angular 22 con todos los talleres de la ruta). Una sola instalación sirve para todas las sesiones.

## Dónde está el código

- Código de la solución: `angular/solucion-de-referencia-app/talleres/nivel-05-arquitectura/05-monorepos-y-microfrontends/`
- Pantalla del taller en la aplicación: ruta `/n5-05/taller`

## Cómo ejecutarla

Desde `angular/solucion-de-referencia-app/` (Node 26 y npm 11; la primera vez `npm ci`):

```bash
cd angular/solucion-de-referencia-app
npm ci
npx ng serve app        # http://localhost:4200/n5-05/taller
```

Para ver el taller con su API simulada (la misma que usan las pruebas E2E):

```bash
npx ng build app && PUERTO=49200 RAIZ=dist/app/browser node servidor/servidor.mjs
# http://localhost:49200/n5-05/taller
```

## Sus pruebas

Comprobaciones adicionales (desde `angular/solucion-de-referencia-app/`):

```bash
(cd proyectos/nx-tienda && npm ci) && node talleres/nivel-05-arquitectura/05-monorepos-y-microfrontends/ejecutar-nx.mjs
(cd proyectos/mfe && npm ci) && node talleres/nivel-05-arquitectura/05-monorepos-y-microfrontends/microfrontend.mjs
```

- `node talleres/nivel-05-arquitectura/05-monorepos-y-microfrontends/ejecutar-nx.mjs`: Monorepo Nx: límites, grafo, afectados, lint, pruebas y build.
- `node talleres/nivel-05-arquitectura/05-monorepos-y-microfrontends/microfrontend.mjs`: Anfitrión y remoto en el navegador.

Todo junto (compilaciones, pruebas, E2E, Nx, microfrontend, Lighthouse): `./verificar.sh` en la carpeta de la aplicación (tarda varios minutos).

## Análisis

Qué se decidió y por qué: [ANALISIS.md](ANALISIS.md).
