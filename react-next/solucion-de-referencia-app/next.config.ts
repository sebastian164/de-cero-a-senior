import type { NextConfig } from 'next';

// Cabeceras de seguridad (taller nivel-06/02). La CSP de scripts usa nonce desde src/proxy.ts.
const cabecerasSeguridad = [
  { key: 'Strict-Transport-Security', value: 'max-age=63072000; includeSubDomains; preload' },
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=()' },
];

const config: NextConfig = {
  cacheComponents: true,
  partialPrefetching: false,
  reactCompiler: process.env.COMPILADOR_REACT === '1',
  // Salida independiente solo al construir la imagen (scripts/construir-imagen.sh): `next start` no la admite y se mide con `next start`.
  output: process.env.SALIDA_INDEPENDIENTE === '1' ? 'standalone' : undefined,
  distDir: process.env.DIST_DIR ?? '.next',
  productionBrowserSourceMaps: true,
  poweredByHeader: false,
  env: { NEXT_PUBLIC_VERSION: process.env.VERSION_APP ?? '1.0.0' },
  typedRoutes: false,
  async headers() {
    return [{ source: '/:ruta*', headers: cabecerasSeguridad }];
  },
};

export default config;
