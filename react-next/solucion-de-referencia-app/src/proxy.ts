import { NextResponse, type NextRequest } from 'next/server';

// Idioma preferido: cookie, luego Accept-Language (taller nivel-06/03, punto 4).
function idiomaPreferido(p: NextRequest): 'es' | 'en' {
  const cookie = p.cookies.get('idioma')?.value;
  if (cookie === 'es' || cookie === 'en') return cookie;
  const cabecera = p.headers.get('accept-language')?.split(',')[0]?.trim().toLowerCase() ?? '';
  return cabecera.startsWith('en') ? 'en' : 'es';
}

// Taller nivel-06/02, punto 6: CSP con nonce por petición para la sección de seguridad.
// (upgrade-insecure-requests se añadiría en producción detrás de HTTPS; aquí se prueba en http://127.0.0.1.)
export function proxy(peticion: NextRequest) {
  if (peticion.nextUrl.pathname === '/tienda') return NextResponse.redirect(new URL(`/${idiomaPreferido(peticion)}/productos`, peticion.url));
  const nonce = Buffer.from(crypto.randomUUID()).toString('base64');
  const csp = [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data:",
    "font-src 'self'",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join('; ');
  const cabeceras = new Headers(peticion.headers);
  cabeceras.set('x-nonce', nonce);
  cabeceras.set('content-security-policy', csp);
  const respuesta = NextResponse.next({ request: { headers: cabeceras } });
  respuesta.headers.set('content-security-policy', csp);
  return respuesta;
}

export const config = { matcher: ['/n6/02-seguridad/:path*', '/tienda'] };
