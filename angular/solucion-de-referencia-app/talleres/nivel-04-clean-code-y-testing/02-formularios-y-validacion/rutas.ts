import { Component } from '@angular/core';
import { Routes, RouterLink } from '@angular/router';
import { ClinicaComponent, sinGuardar } from './clinica.component';

@Component({ imports: [RouterLink], template: `<h1>Salida</h1><p>Saliste del formulario.</p><a routerLink="..">Volver al registro</a>` })
class Salida {}
export default [
  { path: '', component: ClinicaComponent, canDeactivate: [sinGuardar] },
  { path: 'salida', component: Salida },
] satisfies Routes;
