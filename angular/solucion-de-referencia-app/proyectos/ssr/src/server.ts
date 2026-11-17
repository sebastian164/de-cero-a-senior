import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine();

const PRODUCTOS = [{ id: 1, nombre: 'Teclado', precio: 80 }, { id: 2, nombre: 'Ratón', precio: 25 }, { id: 3, nombre: 'Monitor', precio: 300 }];
const peticiones: Record<string, number> = {};
app.get('/api/_contador', (_req, res) => { res.json(peticiones); });
app.post('/api/_reiniciar', (_req, res) => { for (const k of Object.keys(peticiones)) delete peticiones[k]; res.status(204).end(); });
app.get('/api/productos/:id', (req, res) => {
  peticiones[req.params.id] = (peticiones[req.params.id] ?? 0) + 1;
  const p = PRODUCTOS.find((x) => String(x.id) === req.params.id);
  setTimeout(() => (p ? res.json(p) : res.status(404).json({ error: 'no existe' })), 120);
});

/**
 * Example Express Rest API endpoints can be defined here.
 * Uncomment and define endpoints as necessary.
 *
 * Example:
 * ```ts
 * app.get('/api/{*splat}', (req, res) => {
 *   // Handle API request
 * });
 * ```
 */

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
