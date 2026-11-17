import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Pedido } from './pedido';
import { PedidosService } from './pedidos.service';
import { TarjetaPedidoComponent } from './tarjeta-pedido.component';

// pedidos.component.ts — coordina
@Component({
  selector: 'app-pedidos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TarjetaPedidoComponent],
  template: `
    @if (cargando()) {
      <p>Cargando…</p>
    } @else if (error()) {
      <p role="alert">No se pudieron cargar los pedidos.</p>
    } @else {
      @for (pedido of pedidos(); track pedido.id) {
        <app-tarjeta-pedido [pedido]="pedido" (eliminar)="eliminar($event)" />
      } @empty {
        <p>No hay pedidos.</p>
      }
    }
  `,
})
export class PedidosComponent {
  private readonly servicio = inject(PedidosService);

  protected readonly pedidos = signal<Pedido[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal(false);

  constructor() {
    this.servicio.listar().pipe(takeUntilDestroyed()).subscribe({
      next: (pedidos) => { this.pedidos.set(pedidos); this.cargando.set(false); },
      error: () => { this.error.set(true); this.cargando.set(false); },
    });
  }

  protected eliminar(id: string): void {
    this.servicio.eliminar(id).subscribe(() =>
      this.pedidos.update((lista) => lista.filter((p) => p.id !== id)));
  }
}
