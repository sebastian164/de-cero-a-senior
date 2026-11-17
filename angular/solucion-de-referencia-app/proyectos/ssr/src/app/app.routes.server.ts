import { RenderMode, ServerRoute } from '@angular/ssr';

// Punto 2: prerenderizado para el inicio, servidor para el detalle, cliente para el área privada.
export const serverRoutes: ServerRoute[] = [
  { path: '', renderMode: RenderMode.Prerender },
  { path: 'producto/:id', renderMode: RenderMode.Server },
  { path: 'producto-csr/:id', renderMode: RenderMode.Client },
  { path: 'privado', renderMode: RenderMode.Client },
  { path: 'visitas', renderMode: RenderMode.Server },
  { path: '**', renderMode: RenderMode.Server },
];
