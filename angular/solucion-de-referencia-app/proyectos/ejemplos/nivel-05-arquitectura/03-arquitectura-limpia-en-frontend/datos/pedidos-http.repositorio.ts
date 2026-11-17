// datos/pedidos-http.repositorio.ts — adaptador
import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';
import { Pedido } from '../dominio/pedido';
import { RepositorioPedidos } from '../dominio/puertos';

export interface PedidoDto { id: number; fecha_creacion: string; estado: 1 | 2 | 3 | 4; lineas: { producto_id: string; cantidad: number }[]; }
const ESTADOS = { 1: 'pendiente', 2: 'pagado', 3: 'enviado', 4: 'cancelado' } as const;

export const aPedido = (d: PedidoDto): Pedido => ({
  id: String(d.id), estado: ESTADOS[d.estado], creadoEn: new Date(d.fecha_creacion),
  lineas: d.lineas.map((l) => ({ productoId: l.producto_id, cantidad: l.cantidad })),
});

@Injectable()
export class PedidosHttp extends RepositorioPedidos {
  private readonly http = inject(HttpClient);
  listar() { return this.http.get<PedidoDto[]>('/api/pedidos').pipe(map((dtos) => dtos.map(aPedido))); }
  cancelar(id: string) { return this.http.post<void>(`/api/pedidos/${id}/cancelacion`, {}); }
}
