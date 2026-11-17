// dominio/pedido.ts — sin importaciones de Angular
export interface Linea { readonly productoId: string; readonly cantidad: number; }
export interface Pedido {
  readonly id: string;
  readonly estado: 'pendiente' | 'pagado' | 'enviado' | 'cancelado';
  readonly creadoEn: Date;
  readonly lineas: readonly Linea[];
}

export const horasEntre = (desde: Date, hasta: Date): number => (hasta.getTime() - desde.getTime()) / 3_600_000;

export const sePuedeCancelar = (pedido: Pedido, ahora: Date): boolean =>
  (pedido.estado === 'pendiente' || pedido.estado === 'pagado') &&
  horasEntre(pedido.creadoEn, ahora) <= 24;

export class PedidoNoCancelable extends Error {
  constructor(readonly pedidoId: string) { super(`El pedido ${pedidoId} ya no se puede cancelar`); }
}
