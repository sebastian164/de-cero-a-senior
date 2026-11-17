import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Pedido, totalConIva } from './pedido';

// tarjeta-pedido.component.ts — solo presenta
@Component({
  selector: 'app-tarjeta-pedido',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe],
  template: `
    <article>
      <h3>{{ pedido().cliente }}</h3>
      <p>{{ total() | currency: 'COP' }}</p>
      <button type="button" (click)="eliminar.emit(pedido().id)">Eliminar</button>
    </article>
  `,
})
export class TarjetaPedidoComponent {
  readonly pedido = input.required<Pedido>();
  readonly eliminar = output<string>();
  protected readonly total = computed(() => totalConIva(this.pedido()));
}
