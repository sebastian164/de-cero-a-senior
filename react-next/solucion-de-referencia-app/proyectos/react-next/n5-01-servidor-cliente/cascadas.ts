// ---- react-next/nivel-05-arquitectura/01-componentes-de-servidor-y-de-cliente/README.md (bloque 5, desde '// Cascada'), copiado tal cual entre el contexto agregado ----
// Contexto: dos consultas simuladas de 100 ms cada una.
const esperar = <T,>(ms: number, v: T) => new Promise<T>((r) => setTimeout(() => r(v), ms));
const obtenerCliente = (id: string) => esperar(100, { id, nombre: 'Ana' });
const obtenerPedidos = (id: string) => esperar(100, [{ id: 'p1', clienteId: id }]);

export async function enCascada(id: string) {
// Cascada: la segunda espera a la primera sin necesitarla
const cliente = await obtenerCliente(id);
const pedidos = await obtenerPedidos(id);
  return { cliente, pedidos };
}
// ---- react-next/nivel-05-arquitectura/01-componentes-de-servidor-y-de-cliente/README.md (bloque 5, desde '// En paralelo'), copiado tal cual entre el contexto agregado ----
export async function enParalelo(id: string) {
// En paralelo
const [cliente, pedidos] = await Promise.all([obtenerCliente(id), obtenerPedidos(id)]);
  return { cliente, pedidos };
}
