import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { Avisos, ES_RENOVACION, RenovacionDeToken, Sesion } from './sesion';

/** Solo hacia nuestra API: nunca se envía el token a dominios de terceros. */
const esNuestraApi = (url: string) => url.startsWith('/api/');

/** 2. Cabecera de correlación: la misma para la petición y sus reintentos (por eso va primera). */
export const interceptorDeCorrelacion: HttpInterceptorFn = (peticion, siguiente) =>
  siguiente(peticion.headers.has('X-Correlation-Id') ? peticion : peticion.clone({ setHeaders: { 'X-Correlation-Id': crypto.randomUUID() } }));

/** 2. Autenticación: lee el token en cada intento, así el reintento tras renovar lleva el nuevo. */
export const interceptorDeAutenticacion: HttpInterceptorFn = (peticion, siguiente) => {
  const token = inject(Sesion).token();
  return siguiente(token && esNuestraApi(peticion.url) ? peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : peticion);
};

/** 3. Ante un 401, renueva (una sola vez para todas las peticiones simultáneas) y repite la petición. */
export const interceptorDeRenovacion: HttpInterceptorFn = (peticion, siguiente) => {
  if (peticion.context.get(ES_RENOVACION)) return siguiente(peticion);
  const renovacion = inject(RenovacionDeToken);
  const sesion = inject(Sesion);
  return siguiente(peticion).pipe(
    catchError((error: HttpErrorResponse) => (error.status === 401 && sesion.activa()
      ? renovacion.renovar().pipe(switchMap(() => siguiente(peticion)))
      : throwError(() => error))),
  );
};

/** 2. Errores: informa y relanza. Va por fuera de la renovación: solo ve el 401 si renovar no bastó. */
export const interceptorDeErrores: HttpInterceptorFn = (peticion, siguiente) => {
  const avisos = inject(Avisos);
  const router = inject(Router);
  return siguiente(peticion).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) router.navigateByUrl('/n3-05/taller/ingreso');
      else if (error.status >= 500) avisos.error('El servicio no está disponible. Intenta más tarde.');
      return throwError(() => error);
    }),
  );
};

/** El orden importa: el primero ve la petición antes que nadie y la respuesta después que todos. */
export const INTERCEPTORES = [interceptorDeCorrelacion, interceptorDeErrores, interceptorDeRenovacion, interceptorDeAutenticacion];
