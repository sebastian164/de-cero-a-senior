import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SesionDemo } from '../requiere-rol';

@Component({
  imports: [RouterLink],
  template: `<h1>Estructura por funcionalidades (README)</h1>
    <a routerLink="pedidos">Pedidos</a> · <a routerLink="admin">Admin</a>
    <button type="button" (click)="sesion.rol.set('admin')">Ser admin</button>`,
})
export default class InicioComponent { protected readonly sesion = inject(SesionDemo); }
