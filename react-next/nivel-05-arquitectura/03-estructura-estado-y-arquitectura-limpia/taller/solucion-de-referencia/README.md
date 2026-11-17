# Solución de referencia: 03-estructura-estado-y-arquitectura-limpia

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

Esta solución forma parte de la aplicación de referencia de la ruta, que contiene los talleres de `react-next`, `typescript` y `frontend-senior` en un solo proyecto. Su código está en [`../../../../solucion-de-referencia-app/talleres/react-next/nivel-05-arquitectura/03-estructura-estado-y-arquitectura-limpia/`](../../../../solucion-de-referencia-app/talleres/react-next/nivel-05-arquitectura/03-estructura-estado-y-arquitectura-limpia/).

## Cómo ejecutar sus pruebas

```bash
cd react-next/solucion-de-referencia-app
npm ci
docker compose -p ref-react-next up -d --wait   # PostgreSQL en el puerto 49540
npx vitest run talleres/react-next/nivel-05-arquitectura/03-estructura-estado-y-arquitectura-limpia
```

Este taller necesita PostgreSQL: levanta el contenedor con el comando de arriba y bájalo al terminar con `docker compose -p ref-react-next down -v`.

## Pruebas con navegador y comprobaciones extra

Desde `react-next/solucion-de-referencia-app`:

```bash
npx playwright install chromium   # una sola vez
npm run build                      # next build + presupuesto de JavaScript
npx playwright test e2e/n5-03.spec.ts --project=chromium
```

Necesita el PostgreSQL de arriba levantado.

Más detalle de la aplicación (versiones, preparación y todas las pruebas) en [`../../../../solucion-de-referencia-app/README.md`](../../../../solucion-de-referencia-app/README.md).

## Análisis

El análisis está en [ANALISIS.md](ANALISIS.md).
