import { CurrencyPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { aPedido, PedidoDto } from './pedidos';

/** 2. Detalle que depende del id de la ruta (withComponentInputBinding). */
@Component({
  selector: 'app-detalle-recurso',
  imports: [CurrencyPipe, RouterLink],
  template: `
    <nav><a routerLink="../1">Pedido 1 (lento)</a> · <a routerLink="../2">Pedido 2 (rápido)</a> · <a routerLink="../3">Pedido 3</a></nav>
    @if (pedido.isLoading()) {
      <p>Cargando pedido {{ id() }}…</p>
    } @else if (pedido.error()) {
      <p role="alert">No se pudo cargar el pedido.</p>
    } @else if (pedido.hasValue()) {
      <h2>Pedido {{ pedido.value().id }}: {{ pedido.value().cliente }}</h2>
      <p>{{ pedido.value().total | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
    }
  `,
})
export class DetalleRecursoComponent {
  readonly id = input.required<string>();
  protected readonly pedido = httpResource(() => `/api/pedidos/${this.id()}`, { parse: (d) => aPedido(d as PedidoDto) });
}
