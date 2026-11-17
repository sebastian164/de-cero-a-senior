import { loadRemoteModule } from '@angular-architects/native-federation';
import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'inicio' },
  { path: 'inicio', loadComponent: () => import('./inicio').then((m) => m.Inicio) },
  { path: 'resumen', loadComponent: () => loadRemoteModule('remote', './Resumen').then((m) => m.Resumen) },
];
