import { CurrencyPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { Producto, precioConIva } from '../dominio/producto';

@Component({
  selector: 'app-lista-productos',
  imports: [CurrencyPipe],
  template: `<ul>@for (p of productos(); track p.id) { <li>{{ p.nombre }}: {{ conIva(p) | currency: 'COP' : 'symbol-narrow' : '1.0-0' }} con IVA</li> }</ul>`,
})
export class ListaProductos {
  readonly productos = input.required<readonly Producto[]>();
  protected conIva = precioConIva;
}
