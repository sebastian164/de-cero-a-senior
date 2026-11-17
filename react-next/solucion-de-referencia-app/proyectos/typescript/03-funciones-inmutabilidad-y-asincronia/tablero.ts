// ---- typescript/03-funciones-inmutabilidad-y-asincronia/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
// Contexto: tipos y tres consultas simuladas con los retardos del README (300, 500 y 400 ms).
export interface ItemPedido { readonly producto: string; readonly precio: number }
export interface Pedido { readonly id: string; readonly total: number; readonly items: readonly ItemPedido[] }
export interface Tablero { cliente: string; pedidos: readonly string[]; facturas: readonly string[] }

const esperar = <T,>(ms: number, valor: T, falla = false) =>
  new Promise<T>((resolver, rechazar) => setTimeout(() => (falla ? rechazar(new Error('fallo simulado')) : resolver(valor)), ms));
export const simulacion = { fallaPedidos: false };
const obtenerCliente = (id: string) => esperar(300, `cliente-${id}`);
const obtenerPedidos = (id: string) => esperar(500, [`pedido-${id}`], simulacion.fallaPedidos);
const obtenerFacturas = (id: string) => esperar(400, [`factura-${id}`]);

function conDescuento(pedido: Pedido, porcentaje: number): Pedido {
  return {
    ...pedido,
    total: pedido.total - (pedido.total * porcentaje) / 100,
    items: pedido.items.toSorted((a, b) => a.precio - b.precio),
  };
}

async function cargarTablero(id: string): Promise<Tablero> {
  const [cliente, pedidos, facturas] = await Promise.all([
    obtenerCliente(id),
    obtenerPedidos(id),
    obtenerFacturas(id),
  ]);                                               // unos 500 ms: el más lento
  return { cliente, pedidos, facturas };
}

async function cargarTableroTolerante(id: string): Promise<Tablero> {
  const cliente = await obtenerCliente(id);         // crítico: si falla, que falle
  const [pedidos, facturas] = await Promise.allSettled([obtenerPedidos(id), obtenerFacturas(id)]);
  return {
    cliente,
    pedidos: pedidos.status === 'fulfilled' ? pedidos.value : [],
    facturas: facturas.status === 'fulfilled' ? facturas.value : [],
  };
}

// El "Antes" del README, para medir la diferencia (taller TS 03, punto 3).
export async function cargarTableroEnSerie(id: string): Promise<Tablero> {
  const cliente = await obtenerCliente(id);
  const pedidos = await obtenerPedidos(id);
  const facturas = await obtenerFacturas(id);
  return { cliente, pedidos, facturas };
}

export { conDescuento, cargarTablero, cargarTableroTolerante };
