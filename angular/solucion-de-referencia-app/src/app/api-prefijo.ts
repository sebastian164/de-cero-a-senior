import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';

/**
 * Cada sesión tiene su propio juego de datos en el servidor simulado.
 * El código copiado del README pide '/api/pedidos'; este interceptor lo envía a '/api/<sesion>/pedidos',
 * tomando la sesión del primer segmento de la URL que se está activando (/n1-04/taller → n1-04).
 */
export const prefijoApi: HttpInterceptorFn = (peticion, siguiente) => {
  if (!peticion.url.startsWith('/api/')) return siguiente(peticion);
  const router = inject(Router);
  const url = router.currentNavigation()?.finalUrl?.toString() ?? router.url;
  const sesion = url.split(/[/?#]/)[1];
  if (!sesion || peticion.url.startsWith(`/api/${sesion}/`)) return siguiente(peticion);
  return siguiente(peticion.clone({ url: `/api/${sesion}/${peticion.url.slice(5)}` }));
};
