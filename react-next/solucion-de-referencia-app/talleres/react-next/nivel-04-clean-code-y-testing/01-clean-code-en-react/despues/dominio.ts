// Reglas del panel, puras y probadas sin React.
export interface Pedido { readonly id: string; readonly cliente: string; readonly total: number; readonly estado: 'pendiente' | 'pagado' | 'cancelado' }

export const sumarTotal = (pedidos: readonly Pedido[]) => pedidos.reduce((s, p) => s + p.total, 0);
export const pendientesDe = (pedidos: readonly Pedido[]) => pedidos.filter((p) => p.estado === 'pendiente');
export const filtrarPorCliente = (pedidos: readonly Pedido[], texto: string) => pedidos.filter((p) => p.cliente.includes(texto));
export const puedeCobrar = (usuario: { rol: string } | null, pendientes: readonly Pedido[]) =>
  usuario?.rol === 'admin' && pendientes.length > 0;
export const cobrarPendientes = (pedidos: readonly Pedido[]): Pedido[] =>
  pedidos.map((p) => (p.estado === 'pendiente' ? { ...p, estado: 'pagado' } : p));
