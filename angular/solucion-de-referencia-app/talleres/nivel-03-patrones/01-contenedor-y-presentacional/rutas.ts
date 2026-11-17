import { Routes } from '@angular/router';
import { PaginaCursos } from './contenedor';
import { PaginaEjemplos } from './ejemplos';
export default [{ path: '', component: PaginaCursos }, { path: 'ejemplos', component: PaginaEjemplos }] satisfies Routes;
