import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Pedido } from '../dominio/pedido';
import { Producto } from '../../catalogo';   // API pública de otra funcionalidad: permitido

@Component({
  selector: 'app-lista-pedidos',
  imports: [RouterLink],
  template: `<ul>@for (p of pedidos(); track p.id) { <li><a [routerLink]="[p.id]">Pedido {{ p.id }}</a> ({{ p.estado }}, {{ p.lineas.length }} líneas)</li> }</ul>`,
})
export class ListaPedidos {
  readonly pedidos = input.required<readonly Pedido[]>();
  /** Solo para mostrar que se puede usar el tipo público de catálogo. */
  readonly referencia = input<Producto>();
}
