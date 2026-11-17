import { ChangeDetectionStrategy, Component, computed, inject, Injectable, input, output, signal } from '@angular/core';

// Piezas que el README usa sin declarar.
export interface PedidoVista { readonly id: string; readonly cliente: string; readonly urgente: boolean; }
@Injectable({ providedIn: 'root' })
export class AlmacenPedidos {
  private readonly lista = signal<readonly PedidoVista[]>([
    { id: 'p1', cliente: 'Ana', urgente: true }, { id: 'p2', cliente: 'Luis', urgente: false },
  ]);
  readonly pendientes = computed(() => this.lista());
  cancelar(id: string): void { this.lista.update((l) => l.filter((p) => p.id !== id)); }
}
@Injectable({ providedIn: 'root' })
export class Sesion { readonly puedeEditar = signal(true); }

// Presentación: una función de sus entradas
@Component({
  selector: 'app-lista-pedidos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (pedido of pedidos(); track pedido.id) {
      <article [class.urgente]="pedido.urgente">
        <h3>{{ pedido.cliente }}</h3>
        <button type="button" [disabled]="soloLectura()" (click)="cancelar.emit(pedido.id)">Cancelar</button>
      </article>
    } @empty {
      <p>No hay pedidos.</p>
    }
  `,
})
export class ListaPedidosComponent {
  readonly pedidos = input.required<readonly PedidoVista[]>();
  readonly soloLectura = input(false);
  readonly cancelar = output<string>();
}

// Contenedor: conecta con el estado y los servicios
@Component({
  selector: 'app-pagina-pedidos',
  imports: [ListaPedidosComponent],
  template: `<app-lista-pedidos [pedidos]="almacen.pendientes()"
                                [soloLectura]="!sesion.puedeEditar()"
                                (cancelar)="almacen.cancelar($event)" />`,
})
export class PaginaPedidosComponent {
  protected readonly almacen = inject(AlmacenPedidos);
  protected readonly sesion = inject(Sesion);
}
