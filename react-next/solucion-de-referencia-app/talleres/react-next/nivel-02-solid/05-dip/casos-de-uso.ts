// Punto 4: caso de uso de servidor que recibe sus dependencias por parámetro (README: crearCancelarPedido).
export interface PedidoDip { readonly id: string; readonly clienteId: string; readonly creadoEn: Date; readonly estado: 'pendiente' | 'cancelado' }
export interface RepositorioPedidosDip {
  buscar(id: string): Promise<PedidoDip | null>;
  guardar(pedido: PedidoDip): Promise<void>;
}
export type Resultado = { ok: true } | { ok: false; error: string };

const HORAS_PARA_CANCELAR = 24;

export const crearCancelarPedido = (repositorio: RepositorioPedidosDip, reloj: () => Date) =>
  async (pedidoId: string, usuarioId: string): Promise<Resultado> => {
    const pedido = await repositorio.buscar(pedidoId);
    if (!pedido || pedido.clienteId !== usuarioId) return { ok: false, error: 'Pedido no encontrado' };
    if (pedido.estado === 'cancelado') return { ok: false, error: 'El pedido ya estaba cancelado' };
    const horas = (reloj().getTime() - pedido.creadoEn.getTime()) / 3_600_000;
    if (horas > HORAS_PARA_CANCELAR) return { ok: false, error: 'El pedido ya no se puede cancelar' };
    await repositorio.guardar({ ...pedido, estado: 'cancelado' });
    return { ok: true };
  };

export function repositorioEnMemoria(iniciales: readonly PedidoDip[]): RepositorioPedidosDip & { todos(): readonly PedidoDip[] } {
  const pedidos = new Map(iniciales.map((p) => [p.id, p]));
  return {
    buscar: async (id) => pedidos.get(id) ?? null,
    guardar: async (p) => { pedidos.set(p.id, p); },
    todos: () => [...pedidos.values()],
  };
}
