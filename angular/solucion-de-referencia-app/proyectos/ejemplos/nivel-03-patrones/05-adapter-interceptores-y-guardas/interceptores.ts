import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { Avisos, Sesion } from './sesion';

export const interceptorDeAutenticacion: HttpInterceptorFn = (peticion, siguiente) => {
  const token = inject(Sesion).token();
  return siguiente(token ? peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : peticion);
};

export const interceptorDeErrores: HttpInterceptorFn = (peticion, siguiente) => {
  const avisos = inject(Avisos);
  const router = inject(Router);
  return siguiente(peticion).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) router.navigateByUrl('/ingreso');
      else if (error.status >= 500) avisos.error('El servicio no está disponible. Intenta más tarde.');
      return throwError(() => error);        // se informa y se deja que quien llamó decida
    }),
  );
};
