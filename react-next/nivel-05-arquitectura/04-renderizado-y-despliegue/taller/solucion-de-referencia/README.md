# Solución de referencia: 04-renderizado-y-despliegue

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

Esta solución forma parte de la aplicación de referencia de la ruta, que contiene los talleres de `react-next`, `typescript` y `frontend-senior` en un solo proyecto. Su código está en [`../../../../solucion-de-referencia-app/talleres/react-next/nivel-05-arquitectura/04-renderizado-y-despliegue/`](../../../../solucion-de-referencia-app/talleres/react-next/nivel-05-arquitectura/04-renderizado-y-despliegue/).

## Cómo ejecutar sus pruebas

```bash
cd react-next/solucion-de-referencia-app
npm ci
npx playwright install chromium   # una sola vez
npm run build                      # next build + presupuesto de JavaScript
npx playwright test e2e/n5-04.spec.ts --project=chromium
```

## Pruebas con navegador y comprobaciones extra

Desde `react-next/solucion-de-referencia-app`:

```bash
mkdir -p mediciones && npx next build > mediciones/build-completo.txt 2>&1   # guarda la salida de la construcción
python3 -I scripts/comprobar-estrategias.py mediciones/build-completo.txt
bash scripts/replicas.sh
```

`scripts/replicas.sh` construye una imagen y levanta dos contenedores con Docker (tarda unos minutos y los elimina al terminar).

Más detalle de la aplicación (versiones, preparación y todas las pruebas) en [`../../../../solucion-de-referencia-app/README.md`](../../../../solucion-de-referencia-app/README.md).

## Análisis

El análisis está en [ANALISIS.md](ANALISIS.md).
