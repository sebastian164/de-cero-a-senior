import type { MetadataRoute } from 'next';

const BASE = process.env.URL_PUBLICA ?? 'http://127.0.0.1:49510';
export default function robots(): MetadataRoute.Robots {
  return { rules: [{ userAgent: '*', allow: '/', disallow: ['/n5/04-renderizado/mi-cuenta', '/api/'] }], sitemap: `${BASE}/sitemap.xml` };
}
