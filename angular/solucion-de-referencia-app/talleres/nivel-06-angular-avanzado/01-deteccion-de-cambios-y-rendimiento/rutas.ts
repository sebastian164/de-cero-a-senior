import { Routes } from '@angular/router';
import { FilasPlanas, FilasVirtuales, Marco } from './paginas';

export default [{
  path: '', component: Marco, children: [
    { path: '', pathMatch: 'full', redirectTo: 'filas-virtuales' },
    { path: 'filas-planas', component: FilasPlanas },
    { path: 'filas-virtuales', component: FilasVirtuales },
    { path: 'con-defer', loadComponent: () => import('./con-defer').then((m) => m.ConDefer) },
    { path: 'sin-defer', loadComponent: () => import('./sin-defer').then((m) => m.SinDefer) },
  ],
}] satisfies Routes;
