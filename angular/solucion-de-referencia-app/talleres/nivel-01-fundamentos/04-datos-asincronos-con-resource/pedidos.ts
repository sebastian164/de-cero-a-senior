export interface PedidoDto { id: number; cliente_nombre: string; valor_total: number; }
export interface Pedido { readonly id: string; readonly cliente: string; readonly total: number; }
export const aPedido = (dto: PedidoDto): Pedido => ({ id: String(dto.id), cliente: dto.cliente_nombre, total: dto.valor_total });
export const aPedidos = (dtos: unknown): Pedido[] => (dtos as PedidoDto[]).map(aPedido);
/** Regla 2 del README: con menos de dos letras no hay petición. */
export const urlDeBusqueda = (texto: string): string | undefined =>
  texto.trim().length < 2 ? undefined : `/api/pedidos?q=${encodeURIComponent(texto.trim())}`;
