import type { MetadataRoute } from 'next';
import { PRODUCTOS } from './(principal)/n5/04-renderizado/datos';

const BASE = process.env.URL_PUBLICA ?? 'http://127.0.0.1:49510';

// Las fichas de producto salen de los datos; las páginas estáticas, de una lista fija.
export default function sitemap(): MetadataRoute.Sitemap {
  const fijas = ['/n5/04-renderizado/inicio', '/n5/04-renderizado/categoria/bebidas', '/n5/04-renderizado/categoria/despensa'];
  return [
    ...fijas.map((ruta) => ({ url: `${BASE}${ruta}`, changeFrequency: 'weekly' as const })),
    ...PRODUCTOS.map((p) => ({ url: `${BASE}/n5/04-renderizado/producto/${p.id}`, changeFrequency: 'daily' as const })),
  ];
}
