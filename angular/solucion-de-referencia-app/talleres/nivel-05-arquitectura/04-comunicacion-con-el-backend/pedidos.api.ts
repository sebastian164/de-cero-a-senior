import { HttpClient } from '@angular/common/http';
import { inject, Injectable, InjectionToken } from '@angular/core';
import { catchError, forkJoin, map, Observable, retry, switchMap, throwError, timeout, timer } from 'rxjs';
import { aErrorDeDominio, esTransitorio } from './errores';

export interface Pedido { readonly id: string; readonly cliente: string; readonly total: number; }
export interface PedidoDto { id: number; cliente_nombre: string; valor_total: number; }
export const aPedido = (d: PedidoDto): Pedido => ({ id: String(d.id), cliente: d.cliente_nombre, total: d.valor_total });

/** Tiempo límite por intento. Configurable para poder probar el caso "red lenta" sin esperar ocho segundos. */
export const TIEMPO_LIMITE_MS = new InjectionToken<number>('TIEMPO_LIMITE_MS', {
  providedIn: 'root',
  factory: () => Number((typeof location !== 'undefined' && new URLSearchParams(location.search).get('limite')) || 8000),
});

@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);
  private readonly limite = inject(TIEMPO_LIMITE_MS);
  readonly reintentos = { demora: (intento: number) => intento * 500 };

  obtener(id: string): Observable<Pedido> {
    return this.http.get<PedidoDto>(`/api/pedidos/${id}`).pipe(
      timeout(this.limite),
      retry({ count: 2, delay: (error, intento) => (esTransitorio(error) || (error as Error).name === 'TimeoutError' ? timer(this.reintentos.demora(intento)) : throwError(() => error)) }),
      map(aPedido),
      catchError((error) => throwError(() => aErrorDeDominio(error))),
    );
  }

  crear(cliente: string): Observable<{ id: number }> {
    // Un POST NO se reintenta: no es idempotente.
    return this.http.post<{ id: number }>('/api/pedidos', { cliente }).pipe(
      timeout(this.limite),
      catchError((error) => throwError(() => aErrorDeDominio(error))),
    );
  }

  // --- punto 4: cascada y su paralelización ---
  cascada(): Observable<{ usuario: string; pedidos: number; recomendaciones: string[] }> {
    return this.http.get<{ id: string; nombre: string }>('/api/usuario').pipe(
      switchMap((u) => this.http.get<unknown[]>(`/api/usuario/${u.id}/pedidos`).pipe(map((p) => ({ u, p })))),
      switchMap(({ u, p }) => this.http.get<string[]>('/api/recomendaciones').pipe(map((r) => ({ usuario: u.nombre, pedidos: p.length, recomendaciones: r })))),
    );
  }
  paralelo(): Observable<{ usuario: string; pedidos: number; recomendaciones: string[] }> {
    // Las recomendaciones no dependen del usuario: salen a la vez que el usuario; los pedidos sí dependen de su id.
    const recomendaciones$ = this.http.get<string[]>('/api/recomendaciones');
    const usuarioYPedidos$ = this.http.get<{ id: string; nombre: string }>('/api/usuario').pipe(
      switchMap((u) => this.http.get<unknown[]>(`/api/usuario/${u.id}/pedidos`).pipe(map((p) => ({ nombre: u.nombre, pedidos: p.length })))));
    return forkJoin([usuarioYPedidos$, recomendaciones$]).pipe(map(([up, r]) => ({ usuario: up.nombre, pedidos: up.pedidos, recomendaciones: r })));
  }
}
