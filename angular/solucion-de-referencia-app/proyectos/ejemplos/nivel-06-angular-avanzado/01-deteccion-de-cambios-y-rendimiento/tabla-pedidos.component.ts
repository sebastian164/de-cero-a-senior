import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface Pedido { id: number; lineas: { precio: number; cantidad: number }[]; }
/** Contador de llamadas, solo para comprobar que el cálculo no se repite en cada revisión. */
export const llamadas = { total: 0 };
export const total = (p: Pedido): number => { llamadas.total++; return p.lineas.reduce((s, l) => s + l.precio * l.cantidad, 0); };

// Después (README, tal cual)
@Component({
  selector: 'app-tabla-pedidos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe],
  template: `@for (fila of filas(); track fila.id) { <tr><td>{{ fila.total | currency }}</td></tr> }`,
})
export class TablaPedidosComponent {
  readonly pedidos = input.required<readonly Pedido[]>();
  protected readonly filas = computed(() =>
    this.pedidos().map((p) => ({ id: p.id, total: total(p) })));   // se recalcula solo si cambia la lista
}
