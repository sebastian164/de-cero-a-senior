# Solución de referencia: 02-calidad-entrega-y-observabilidad

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

Esta solución forma parte de la aplicación de referencia de la ruta, que contiene los talleres de `react-next`, `typescript` y `frontend-senior` en un solo proyecto. Su código está en [`../../../../react-next/solucion-de-referencia-app/talleres/frontend-senior/02-calidad-entrega-y-observabilidad/`](../../../../react-next/solucion-de-referencia-app/talleres/frontend-senior/02-calidad-entrega-y-observabilidad/).

## Cómo ejecutar sus pruebas

```bash
cd react-next/solucion-de-referencia-app
npm ci
npx vitest run talleres/frontend-senior/02-calidad-entrega-y-observabilidad
```

## Pruebas con navegador y comprobaciones extra

Desde `react-next/solucion-de-referencia-app`:

```bash
npx playwright install chromium   # una sola vez
npm run build                      # next build + presupuesto de JavaScript
npx playwright test e2e/fe-02.spec.ts --project=chromium
bash scripts/vista-previa.sh levantar 7 && bash scripts/vista-previa.sh bajar 7
```

`scripts/vista-previa.sh` construye una imagen y levanta un contenedor por PR con Docker; `bajar` lo elimina.

Más detalle de la aplicación (versiones, preparación y todas las pruebas) en [`../../../../react-next/solucion-de-referencia-app/README.md`](../../../../react-next/solucion-de-referencia-app/README.md).

## Análisis

El análisis está en [ANALISIS.md](ANALISIS.md).
