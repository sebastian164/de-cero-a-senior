import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, Injectable, input, output, signal } from '@angular/core';
import { map, Observable, of } from 'rxjs';

// ---- Dominio (las funciones y fábricas que el README da por supuestas) ----
export type EstadoPedido = 'pendiente' | 'pagado';
export interface Linea { precio: number; cantidad: number; }
export interface Pedido { id: string; cliente: string; estado: EstadoPedido; lineas: Linea[]; total: number; }

export const IVA = 0.19;
export const total = (p: { lineas: Linea[] }) => p.lineas.reduce((s, l) => s + l.precio * l.cantidad, 0) * (1 + IVA);

/** Fábrica de datos: cada prueba declara solo lo que le importa. */
export const unPedido = (extra: Partial<Pedido> = {}): Pedido => ({ id: 'p1', cliente: 'Ana', estado: 'pendiente', lineas: [], total: 0, ...extra });

// ---- Servicio HTTP: traduce el DTO del servidor al modelo ----
interface PedidoDto { id: number; estado: 1 | 2; valor_centavos: number; cliente?: string; }

@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);
  listar(): Observable<Pedido[]> {
    return this.http.get<PedidoDto[]>('/api/pedidos').pipe(map((dtos) => dtos.map((d): Pedido => ({
      id: String(d.id), cliente: d.cliente ?? '', estado: d.estado === 1 ? 'pendiente' : 'pagado', lineas: [], total: d.valor_centavos / 100,
    }))));
  }
  cancelar(id: string): Observable<void> { return this.http.delete<void>(`/api/pedidos/${id}`); }
}

// ---- Almacén ----
@Injectable({ providedIn: 'root' })
export class AlmacenPedidos {
  private readonly api = inject(PedidosApi);
  private readonly pedidos = signal<Pedido[]>([]);
  private readonly filtro = signal<EstadoPedido | 'todos'>('todos');
  readonly error = signal('');
  readonly visibles = computed(() => this.filtro() === 'todos' ? this.pedidos() : this.pedidos().filter((p) => p.estado === this.filtro()));

  cargar() { this.api.listar().subscribe({ next: (p) => this.pedidos.set(p), error: () => this.error.set('No se pudieron cargar los pedidos') }); }
  filtrar(estado: EstadoPedido | 'todos') { this.filtro.set(estado); }
  /** Actualización optimista con reversa si la API falla. */
  cancelar(id: string) {
    const antes = this.pedidos();
    this.pedidos.set(antes.filter((p) => p.id !== id));
    this.api.cancelar(id).subscribe({ error: () => { this.pedidos.set(antes); this.error.set('No se pudo cancelar'); } });
  }
}

// ---- Componente de presentación ----
@Component({
  selector: 'app-lista-pedidos',
  template: `
    @for (p of pedidos(); track p.id) {
      <li>{{ p.cliente }} <button type="button" (click)="cancelar.emit(p.id)">Cancelar pedido de {{ p.cliente }}</button></li>
    } @empty { <p>No hay pedidos.</p> }
  `,
})
export class ListaPedidosComponent {
  readonly pedidos = input.required<Pedido[]>();
  readonly cancelar = output<string>();
}

