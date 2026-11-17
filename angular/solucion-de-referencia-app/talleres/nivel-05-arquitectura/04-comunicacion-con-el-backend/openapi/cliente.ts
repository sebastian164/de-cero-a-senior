// Usa los tipos generados desde la especificación: si la especificación cambia de forma incompatible, esto deja de compilar.
import type { components, paths } from './api.generado';

export type PedidoDto = components['schemas']['PedidoDto'];
export type RespuestaObtener = paths['/api/pedidos/{id}']['get']['responses']['200']['content']['application/json'];

export const aPedido = (d: PedidoDto) => ({ id: String(d.id), cliente: d.cliente_nombre, total: d.valor_total });
export const nuevo = (cliente: string): components['schemas']['NuevoPedido'] => ({ cliente });
