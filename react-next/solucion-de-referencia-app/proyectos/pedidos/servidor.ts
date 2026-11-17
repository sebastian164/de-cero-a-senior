// Contexto de la sesión nivel-01/03 que el README da por supuesto: sesión y repositorio en memoria.
import 'server-only';

export interface Pedido { readonly id: string; readonly cliente: string; readonly valor: number; readonly estado: 'activo' | 'cancelado'; readonly usuarioId: string }

const pedidos: Pedido[] = [
  { id: '1', cliente: 'Ana', valor: 120000, estado: 'activo', usuarioId: 'u-1' },
  { id: '2', cliente: 'Luis', valor: 45000, estado: 'activo', usuarioId: 'u-1' },
  { id: '3', cliente: 'Eva', valor: 99000, estado: 'activo', usuarioId: 'u-2' },
];

export async function exigirSesion(): Promise<{ usuarioId: string }> {
  return { usuarioId: 'u-1' }; // en la demo siempre hay sesión; el taller nivel-06/02 la implementa de verdad
}

export const repositorio = {
  async listar(): Promise<readonly Pedido[]> {
    return pedidos.filter((p) => p.estado === 'activo');
  },
  async buscar(id: string): Promise<Pedido | undefined> {
    return pedidos.find((p) => p.id === id);
  },
  async cancelar(pedidoId: string, usuarioId: string): Promise<void> {
    const i = pedidos.findIndex((p) => p.id === pedidoId);
    if (i === -1 || pedidos[i]!.usuarioId !== usuarioId) throw new Error('Pedido no encontrado');
    pedidos[i] = { ...pedidos[i]!, estado: 'cancelado' };
  },
};
