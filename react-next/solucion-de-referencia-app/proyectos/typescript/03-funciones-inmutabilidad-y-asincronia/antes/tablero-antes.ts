// Declaraciones de apoyo para que el bloque compile (no están en el README).
interface Pedido { total: number; items: { producto: string; precio: number }[] }
export const retardos = { cliente: 300, pedidos: 500, facturas: 400 };
const esperar = <T,>(ms: number, valor: T) => new Promise<T>((r) => setTimeout(() => r(valor), ms));
const obtenerCliente = (id: string) => esperar(retardos.cliente, { id });
const obtenerPedidos = (id: string) => esperar(retardos.pedidos, [id]);
const obtenerFacturas = (id: string) => esperar(retardos.facturas, [id]);

function aplicarDescuento(pedido: Pedido, porcentaje: number) {
  pedido.total = pedido.total - (pedido.total * porcentaje) / 100;   // modifica el argumento
  pedido.items.sort((a, b) => a.precio - b.precio);                  // y reordena la lista de otro
  return pedido;
}

async function cargarTablero(id: string) {
  const cliente = await obtenerCliente(id);        // 300 ms
  const pedidos = await obtenerPedidos(id);        // 500 ms, y no dependía de la anterior
  const facturas = await obtenerFacturas(id);      // 400 ms
  return { cliente, pedidos, facturas };           // 1.200 ms en total, sin manejo de errores
}

export { aplicarDescuento, cargarTablero, type Pedido };
