import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, input, signal, Component } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, switchMap, Observable } from 'rxjs';

export interface Item { nombre: string; p: number; c: number; }
export interface Pedido { id: string; items: Item[]; }

export type Carga<T> =
  | { estado: 'cargando' }
  | { estado: 'error'; mensaje: string }
  | { estado: 'listo'; datos: T };

// Funciones puras que el README da por supuestas (filtrarPorNombre, sumarImportes).
export const filtrarPorNombre = (items: readonly Item[], filtro: string) => items.filter((i) => i.nombre.includes(filtro));
export const sumarImportes = (items: readonly Item[]) => items.reduce((s, i) => s + i.p * i.c, 0);

@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);
  obtener(id: string): Observable<Pedido> { return this.http.get<Pedido>(`/api/pedidos/${id}`); }
}

// --- Después (README, copiado) ---
@Component({
  selector: 'app-resumen',
  template: `
    @switch (carga().estado) {
      @case ('cargando') { <p role="status">Cargando…</p> }
      @case ('error') { <p role="alert">No se pudo cargar el pedido</p> }
      @default {
        <label>Filtro <input #f [value]="filtro()" (input)="filtro.set(f.value)" /></label>
        <ul>@for (i of itemsVisibles(); track i.nombre) { <li>{{ i.nombre }} × {{ i.c }}</li> }</ul>
        <p>Total: {{ total() }}</p>
      }
    }
  `,
})
export class ResumenComponent {
  private readonly api = inject(PedidosApi);

  readonly pedidoId = input.required<string>();
  protected readonly filtro = signal('');

  protected readonly carga = toSignal(
    toObservable(this.pedidoId).pipe(switchMap((id) => this.api.obtener(id).pipe(
      map((datos): Carga<Pedido> => ({ estado: 'listo', datos })),
      startWith<Carga<Pedido>>({ estado: 'cargando' }),
      catchError(() => of<Carga<Pedido>>({ estado: 'error', mensaje: 'No se pudo cargar el pedido' })),
    ))),
    { initialValue: { estado: 'cargando' } as Carga<Pedido> },
  );

  protected readonly itemsVisibles = computed(() => {
    const carga = this.carga();
    return carga.estado === 'listo' ? filtrarPorNombre(carga.datos.items, this.filtro()) : [];
  });
  protected readonly total = computed(() => sumarImportes(this.itemsVisibles()));
}
