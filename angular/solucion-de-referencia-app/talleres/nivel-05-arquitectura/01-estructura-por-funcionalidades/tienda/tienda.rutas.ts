// Versión con CARGA DIFERIDA (la que usa la aplicación).
import { Component } from '@angular/core';
import { RouterOutlet, Routes } from '@angular/router';
import { Inicio } from './inicio';
import { requiereRol } from './nucleo/sesion';

@Component({ template: `<h1>Sin permiso</h1><p role="alert">Necesitas el rol de administración.</p>` })
class SinPermisoPagina {}
@Component({ imports: [RouterOutlet], template: `<router-outlet />` })
class Marco {}

/** `base` es la URL donde se monta la tienda; la guarda de `admin` redirige a `<base>/sin-permiso`. */
export const crearRutas = (base: string) => [{
  path: '', component: Marco, children: [
    { path: '', component: Inicio },
    { path: 'sin-permiso', component: SinPermisoPagina },
    { path: 'catalogo', loadChildren: () => import('./catalogo/catalogo.rutas') },
    { path: 'pedidos', loadChildren: () => import('./pedidos') .then((m) => m.pedidosRutas) },
    { path: 'admin', canMatch: [requiereRol('admin', `${base}/sin-permiso`)], loadChildren: () => import('./admin').then((m) => m.adminRutas) },
  ],
}] satisfies Routes;
export default crearRutas('/n5-01/taller');
