import { inject, Injectable } from '@angular/core';
import { CanActivateFn, CanDeactivateFn, Router } from '@angular/router';
import { Rol, Sesion } from './sesion';

const INGRESO = '/n3-05/taller/ingreso';

export const requiereSesion: CanActivateFn = (_ruta, estado) =>
  inject(Sesion).activa() || inject(Router).createUrlTree([INGRESO], { queryParams: { volverA: estado.url } });

export const requiereRol = (rol: Rol): CanActivateFn => () =>
  inject(Sesion).tieneRol(rol) || inject(Router).createUrlTree(['/n3-05/taller/sin-permiso']);

/** Lo implementa cualquier pantalla con un formulario. */
export interface ConCambiosSinGuardar { tieneCambiosSinGuardar(): boolean; }

/** Envoltura de window.confirm, para poder reemplazarla en las pruebas. */
@Injectable({ providedIn: 'root' })
export class Confirmacion { preguntar(mensaje: string): boolean { return window.confirm(mensaje); } }

export const sinCambiosPerdidos: CanDeactivateFn<ConCambiosSinGuardar> = (componente) =>
  !componente.tieneCambiosSinGuardar() || inject(Confirmacion).preguntar('Hay cambios sin guardar. ¿Salir de todos modos?');
