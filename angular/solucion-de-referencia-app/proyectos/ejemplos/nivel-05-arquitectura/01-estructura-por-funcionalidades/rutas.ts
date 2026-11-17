import { Routes } from '@angular/router';
import { requiereRol } from './requiere-rol';

// Tabla de rutas del README (con rutas relativas a /n5-01/ejemplo).
export const rutas: Routes = [
  { path: '', loadComponent: () => import('./inicio/inicio.component') },
  { path: 'pedidos', loadChildren: () => import('./pedidos/pedidos.rutas') },
  { path: 'admin', canMatch: [requiereRol('admin')], loadChildren: () => import('./admin/admin.rutas') },
];
export default rutas;
