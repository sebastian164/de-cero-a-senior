import { inject } from '@angular/core';
import { CanActivateFn, ResolveFn, Router } from '@angular/router';
import { Pedido, PedidosApi } from './adaptador';
import { Rol, Sesion } from './sesion';

export const requiereSesion: CanActivateFn = (_ruta, estado) => {
  const sesion = inject(Sesion);
  return sesion.activa()
    ? true
    : inject(Router).createUrlTree(['/ingreso'], { queryParams: { volverA: estado.url } });
};

export const requiereRol = (rol: Rol): CanActivateFn => () =>
  inject(Sesion).tieneRol(rol) || inject(Router).createUrlTree(['/sin-permiso']);

export const pedidoDeLaRuta: ResolveFn<Pedido> = (ruta) =>
  inject(PedidosApi).obtener(ruta.paramMap.get('id')!);
