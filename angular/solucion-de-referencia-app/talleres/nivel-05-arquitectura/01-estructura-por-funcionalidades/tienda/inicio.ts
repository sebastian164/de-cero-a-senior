import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Sesion } from './nucleo/sesion';
import { precioConIva } from './catalogo';

@Component({
  imports: [RouterLink],
  template: `
    <h1>Tienda por funcionalidades</h1>
    <nav aria-label="Tienda">
      <a routerLink="catalogo">Catálogo</a> · <a routerLink="pedidos">Pedidos</a> · <a routerLink="admin">Administración</a>
      <button type="button" (click)="sesion.entrarComo('admin')">Entrar como admin</button>
    </nav>
    <p>Precio de 1000 con IVA: {{ ejemplo }}</p>
  `,
})
export class Inicio { protected readonly sesion = inject(Sesion); protected readonly ejemplo = precioConIva({ id: 'x', nombre: 'x', precio: 1000 }); }
export class SinPermiso {}
