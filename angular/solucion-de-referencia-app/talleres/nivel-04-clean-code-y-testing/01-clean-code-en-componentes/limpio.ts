import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, Injectable, input, output } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, Observable, of, startWith, switchMap } from 'rxjs';

// ---- Dominio y reglas puras ----
export interface Item { readonly nombre: string; readonly precio: number; readonly cantidad: number; readonly activo: boolean; }
export interface Pedido { readonly id: string; readonly dueno: string; readonly cerrado: boolean; readonly items: readonly Item[]; }
export interface Usuario { readonly id: string; readonly rol: 'admin' | 'cliente'; }
export const IVA = 0.19;

export type Carga<T> =
  | { estado: 'cargando' }
  | { estado: 'error'; mensaje: string }
  | { estado: 'listo'; datos: T };

export const itemsActivos = (items: readonly Item[]) => items.filter((i) => i.activo);
export const totalConIva = (items: readonly Item[]) => itemsActivos(items).reduce((s, i) => s + i.precio * i.cantidad, 0) * (1 + IVA);
export const puedeEditarPedido = (u: Usuario | undefined, p: Pedido) => u?.rol === 'admin' && !p.cerrado && p.dueno === u.id;

@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);
  obtener(id: string): Observable<Pedido> { return this.http.get<Pedido>(`/api/pedidos/${id}`); }
}

// ---- Componente: la API pública son solo input y output ----
@Component({
  selector: 'app-pedido-limpio',
  template: `
    @switch (carga().estado) {
      @case ('cargando') { <p role="status">Cargando…</p> }
      @case ('error') { <p role="alert">No se pudo cargar el pedido</p> }
      @default {
        <p>Total con IVA: {{ totalConIva() }}</p>
        <p>Items activos: {{ cantidadActiva() }}</p>
        @if (hayItemsActivos()) { <p>Hay items activos</p> }
        @if (puedeEditar()) { <button type="button" (click)="editarPedido()">Editar pedido</button> }
      }
    }
  `,
})
export class PedidoLimpioComponent {
  private readonly api = inject(PedidosApi);

  readonly pedidoId = input.required<string>();
  readonly usuario = input<Usuario>();
  readonly edicionSolicitada = output<string>();

  private readonly carga$ = toObservable(this.pedidoId).pipe(switchMap((id) => this.api.obtener(id).pipe(
    map((datos): Carga<Pedido> => ({ estado: 'listo', datos })),
    startWith<Carga<Pedido>>({ estado: 'cargando' }),
    catchError(() => of<Carga<Pedido>>({ estado: 'error', mensaje: 'No se pudo cargar el pedido' })),
  )));
  protected readonly carga = toSignal(this.carga$, { initialValue: { estado: 'cargando' } as Carga<Pedido> });

  // Estado mínimo: solo `carga`; todo lo demás se deriva.
  private readonly pedido = computed(() => { const c = this.carga(); return c.estado === 'listo' ? c.datos : undefined; });
  protected readonly cantidadActiva = computed(() => itemsActivos(this.pedido()?.items ?? []).length);
  protected readonly hayItemsActivos = computed(() => this.cantidadActiva() > 0);
  protected readonly totalConIva = computed(() => Math.round(totalConIva(this.pedido()?.items ?? [])));
  protected readonly puedeEditar = computed(() => { const p = this.pedido(); return !!p && puedeEditarPedido(this.usuario(), p); });

  protected editarPedido(): void { this.edicionSolicitada.emit(this.pedidoId()); }
}
