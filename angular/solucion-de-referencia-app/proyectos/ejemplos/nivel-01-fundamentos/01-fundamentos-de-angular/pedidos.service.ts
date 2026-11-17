import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Pedido } from './pedido';

// pedidos.service.ts
@Injectable({ providedIn: 'root' })
export class PedidosService {
  private readonly http = inject(HttpClient);

  listar(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>('/api/pedidos');
  }

  eliminar(id: string): Observable<void> {
    return this.http.delete<void>(`/api/pedidos/${id}`);
  }
}
