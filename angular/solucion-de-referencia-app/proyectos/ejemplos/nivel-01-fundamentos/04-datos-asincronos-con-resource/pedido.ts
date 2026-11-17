// Piezas que el README usa sin declarar: el DTO, el modelo y el adaptador.
export interface PedidoDto { id: number; cliente_nombre: string; valor_total: number; }
export interface Pedido { readonly id: string; readonly cliente: string; readonly total: number; }
export const aPedido = (dto: PedidoDto): Pedido => ({ id: String(dto.id), cliente: dto.cliente_nombre, total: dto.valor_total });
