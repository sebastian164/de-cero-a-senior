import { CurrencyPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Component, inject, input, resource } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { aPedido, PedidoDto } from './pedido';
import { PedidosApi } from './pedidos.api';

@Component({
  selector: 'app-detalle-pedido',
  imports: [CurrencyPipe],
  template: `
    @if (pedido.isLoading()) {
      <p>Cargando…</p>
    } @else if (pedido.error()) {
      <p role="alert">No se pudo cargar el pedido.</p>
      <button type="button" (click)="pedido.reload()">Reintentar</button>
    } @else if (pedido.hasValue()) {
      <h2>Pedido {{ pedido.value().id }}</h2>
      <p>{{ pedido.value().total | currency: 'COP' }}</p>
    }
  `,
})
export class DetallePedidoComponent {
  readonly id = input.required<string>();

  protected readonly pedido = httpResource(
    () => `/api/pedidos/${this.id()}`,                       // se vuelve a pedir cuando cambia id
    { parse: (dto) => aPedido(dto as PedidoDto) },            // el adaptador, en el borde
  );
}

// Las dos variantes del README, en un componente aparte para probarlas.
@Component({
  selector: 'app-detalle-pedido-variantes',
  template: `
    @if (pedido.hasValue()) { <p>{{ pedido.value().cliente }}</p> }
    @if (historial.hasValue()) { <ul>@for (h of historial.value(); track h) { <li>{{ h }}</li> }</ul> }
  `,
})
export class DetallePedidoVariantesComponent {
  private readonly api = inject(PedidosApi);
  readonly id = input.required<string>();

  protected readonly pedido = resource({
    params: () => ({ id: this.id() }),
    loader: ({ params, abortSignal }) => this.api.obtenerComoPromesa(params.id, abortSignal),
  });

  protected readonly historial = rxResource({
    params: () => ({ id: this.id() }),
    stream: ({ params }) => this.api.historial(params.id),      // devuelve un observable
  });
}
