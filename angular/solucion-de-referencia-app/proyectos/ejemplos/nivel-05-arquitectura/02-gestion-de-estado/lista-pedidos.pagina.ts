import { HttpClient } from '@angular/common/http';
import { Component, inject, Injectable, numberAttribute, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { combineLatest, map, Observable, switchMap } from 'rxjs';

export type EstadoPedido = 'pendiente' | 'pagado' | 'enviado';
export interface PedidoResumen { id: string; estado: EstadoPedido; cliente: string; }

@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);
  listar(f: { estado: EstadoPedido | 'todos'; pagina: number }): Observable<PedidoResumen[]> {
    return this.http.get<{ items: PedidoResumen[] }>('/api/pedidos', { params: { estado: f.estado, pagina: f.pagina } }).pipe(map((r) => r.items));
  }
}

// Después: la URL es la fuente de verdad (README)
@Component({
  selector: 'app-lista-pedidos-pagina',
  template: `
    <h1>Pedidos</h1>
    <button type="button" (click)="filtrar('pendiente')">Pendientes</button>
    <button type="button" (click)="filtrar('pagado')">Pagados</button>
    <button type="button" (click)="filtrar('todos')">Todos</button>
    <p>Filtro: <strong>{{ estado() }}</strong> · página {{ pagina() }}</p>
    <ul>@for (p of pedidos(); track p.id) { <li>{{ p.id }} {{ p.cliente }} ({{ p.estado }})</li> }</ul>
  `,
})
export class ListaPedidosPagina {
  private readonly router = inject(Router);
  private readonly api = inject(PedidosApi);

  // Enlazadas al parámetro de consulta. Si la URL no trae el parámetro, el enlace de entradas del router
  // escribe `undefined` (no deja el valor por defecto): el `transform` devuelve el valor por defecto.
  readonly estado = input<EstadoPedido | 'todos', EstadoPedido | 'todos' | undefined>('todos', { transform: (v) => v ?? 'todos' });
  readonly pagina = input(1, { transform: (v: unknown) => numberAttribute(v, 1) });

  protected readonly pedidos = toSignal(
    combineLatest([toObservable(this.estado), toObservable(this.pagina)]).pipe(
      switchMap(([estado, pagina]) => this.api.listar({ estado, pagina }))),
    { initialValue: [] },
  );

  protected filtrar(estado: EstadoPedido | 'todos'): void {
    this.router.navigate([], { queryParams: { estado, pagina: 1 }, queryParamsHandling: 'merge' });
  }
}
// Requiere provideRouter(rutas, withComponentInputBinding())
