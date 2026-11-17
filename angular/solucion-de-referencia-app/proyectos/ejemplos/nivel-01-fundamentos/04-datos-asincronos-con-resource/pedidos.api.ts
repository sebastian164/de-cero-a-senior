import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { aPedido, Pedido, PedidoDto } from './pedido';

@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);
  async obtenerComoPromesa(id: string, abortSignal: AbortSignal): Promise<Pedido> {
    const respuesta = await fetch(`/api/n1-04/pedidos/${id}`, { signal: abortSignal });
    if (!respuesta.ok) throw new Error(`HTTP ${respuesta.status}`);
    return aPedido(await respuesta.json());
  }
  historial(id: string): Observable<string[]> {
    return this.http.get<string[]>(`/api/pedidos/${id}/historial`);
  }
}
