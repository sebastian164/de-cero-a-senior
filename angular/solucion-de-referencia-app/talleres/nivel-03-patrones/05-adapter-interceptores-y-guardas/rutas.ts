import { provideHttpClient, withInterceptors, withRequestsMadeViaParent } from '@angular/common/http';
import { Routes } from '@angular/router';
import { requiereRol, requiereSesion, sinCambiosPerdidos } from './guardas';
import { INTERCEPTORES } from './interceptores';
import { PaginaAdmin, PaginaBlog, PaginaEditor, PaginaIngreso, PaginaSinPermiso } from './pantallas';

export default [
  {
    path: '',
    providers: [provideHttpClient(withInterceptors(INTERCEPTORES), withRequestsMadeViaParent())],
    children: [
      { path: '', component: PaginaBlog },
      { path: 'admin', canActivate: [requiereSesion, requiereRol('admin')], component: PaginaAdmin },
      { path: 'editor', canActivate: [requiereSesion], canDeactivate: [sinCambiosPerdidos], component: PaginaEditor },
      { path: 'ingreso', component: PaginaIngreso },
      { path: 'sin-permiso', component: PaginaSinPermiso },
    ],
  },
] satisfies Routes;
