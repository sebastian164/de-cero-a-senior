import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

// Lo que envía el servidor
interface PedidoDto {
  id: number;
  fecha_creacion: string;
  cliente: { nombre_completo: string };
  estado: 1 | 2 | 3;
  valor_centavos: number;
}

// Lo que usa la aplicación
export interface Pedido {
  readonly id: string;
  readonly creadoEn: Date;
  readonly cliente: string;
  readonly estado: 'pendiente' | 'pagado' | 'cancelado';
  readonly total: number;
}

const ESTADOS = { 1: 'pendiente', 2: 'pagado', 3: 'cancelado' } as const;

export const aPedido = (dto: PedidoDto): Pedido => ({
  id: String(dto.id),
  creadoEn: new Date(dto.fecha_creacion),
  cliente: dto.cliente.nombre_completo,
  estado: ESTADOS[dto.estado],
  total: dto.valor_centavos / 100,
});

@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);
  listar(): Observable<Pedido[]> {
    return this.http.get<PedidoDto[]>('/api/pedidos').pipe(map((dtos) => dtos.map(aPedido)));
  }
  // Fuera del README: el resolver lo usa sin declararlo.
  obtener(id: string): Observable<Pedido> {
    return this.http.get<PedidoDto>(`/api/pedidos/${id}`).pipe(map(aPedido));
  }
}
