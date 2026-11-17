import { HttpClient, httpResource } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { aPedidos } from './pedidos';

/** 5. Las escrituras van por HttpClient en un servicio. */
@Injectable({ providedIn: 'root' })
export class PedidosEscritura {
  private readonly http = inject(HttpClient);
  eliminar(id: string): Observable<void> { return this.http.delete<void>(`/api/pedidos/${id}`); }
}

/** 7. El recurso de la lista vive en un servicio: los componentes que lo leen comparten UNA petición. */
@Injectable({ providedIn: 'root' })
export class ListaDePedidos {
  readonly recurso = httpResource(() => '/api/pedidos', { parse: aPedidos, defaultValue: [] });
}
