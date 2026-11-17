import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

// Piezas que el README usa sin declarar.
export type EstadoPedido = 'pendiente' | 'pagado' | 'cancelado';
export interface Pedido { readonly id: string; readonly cliente: string; readonly estado: EstadoPedido; readonly total: number; }
@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);
  listar(): Observable<Pedido[]> { return this.http.get<Pedido[]>('/api/pedidos'); }
  cancelar(id: string): Observable<void> { return this.http.post<void>(`/api/pedidos/${id}/cancelacion`, {}); }
}

interface EstadoPedidos {
  readonly pedidos: readonly Pedido[];
  readonly cargando: boolean;
  readonly error: string | null;
  readonly filtro: EstadoPedido | 'todos';
}

@Injectable({ providedIn: 'root' })
export class AlmacenPedidos {
  private readonly api = inject(PedidosApi);

  private readonly estado = signal<EstadoPedidos>({
    pedidos: [], cargando: false, error: null, filtro: 'todos',
  });

  // Lectura: selectores
  readonly cargando = computed(() => this.estado().cargando);
  readonly error = computed(() => this.estado().error);
  readonly visibles = computed(() => {
    const { pedidos, filtro } = this.estado();
    return filtro === 'todos' ? pedidos : pedidos.filter((p) => p.estado === filtro);
  });
  readonly totalPendiente = computed(() =>
    this.estado().pedidos.filter((p) => p.estado === 'pendiente').reduce((s, p) => s + p.total, 0));

  // Escritura: intenciones
  cargar(): void {
    this.estado.update((e) => ({ ...e, cargando: true, error: null }));
    this.api.listar().subscribe({
      next: (pedidos) => this.estado.update((e) => ({ ...e, pedidos, cargando: false })),
      error: () => this.estado.update((e) => ({ ...e, cargando: false, error: 'No se pudieron cargar los pedidos' })),
    });
  }

  filtrar(filtro: EstadoPedidos['filtro']): void {
    this.estado.update((e) => ({ ...e, filtro }));
  }

  cancelar(id: string): void {
    const anterior = this.estado().pedidos;
    this.estado.update((e) => ({ ...e, pedidos: e.pedidos.filter((p) => p.id !== id) }));   // optimista
    this.api.cancelar(id).subscribe({
      error: () => this.estado.update((e) => ({ ...e, pedidos: anterior, error: 'No se pudo cancelar' })),
    });
  }
}
