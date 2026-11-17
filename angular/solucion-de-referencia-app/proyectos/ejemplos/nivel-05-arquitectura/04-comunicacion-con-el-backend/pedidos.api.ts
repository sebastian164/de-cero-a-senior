import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, retry, throwError, timeout, timer } from 'rxjs';

export interface Pedido { readonly id: string; readonly cliente: string; readonly total: number; }
interface PedidoDto { id: number; cliente_nombre: string; valor_total: number; }
export const aPedido = (d: PedidoDto): Pedido => ({ id: String(d.id), cliente: d.cliente_nombre, total: d.valor_total });

export class PedidoNoEncontrado extends Error {}
export class SinConexion extends Error {}
export class ErrorDeValidacion extends Error {
  constructor(readonly campos: Record<string, string>) { super('Datos inválidos'); }
}

const aErrorDeDominio = (error: HttpErrorResponse): Error => {
  if (error.status === 0) return new SinConexion();
  if (error.status === 404) return new PedidoNoEncontrado();
  if (error.status === 422) return new ErrorDeValidacion(error.error?.campos ?? {});
  return error;
};

/** Solo se reintenta lo que puede arreglarse solo: fallos de red y 5xx. */
const esTransitorio = (error: unknown) => error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);

@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);

  obtener(id: string): Observable<Pedido> {
    return this.http.get<PedidoDto>(`/api/pedidos/${id}`).pipe(
      timeout(8000),
      retry({ count: 2, delay: (error, intento) => (esTransitorio(error) ? timer(intento * 500) : throwError(() => error)) }),   // solo porque GET es idempotente
      map(aPedido),
      catchError((error) => throwError(() => aErrorDeDominio(error))),
    );
  }
}
