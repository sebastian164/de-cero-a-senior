// Punto 3: la lógica de una tabla filtrable extraída primero a funciones puras...
export interface Fila { readonly id: string; readonly cliente: string; readonly total: number }
export type Orden = { campo: 'cliente' | 'total'; sentido: 'asc' | 'desc' };

export const filtrar = (filas: readonly Fila[], texto: string) =>
  texto.trim() === '' ? filas : filas.filter((f) => f.cliente.toLowerCase().includes(texto.trim().toLowerCase()));

export function ordenar(filas: readonly Fila[], orden: Orden): Fila[] {
  const factor = orden.sentido === 'asc' ? 1 : -1;
  return filas.toSorted((a, b) => factor * (orden.campo === 'total' ? a.total - b.total : a.cliente.localeCompare(b.cliente)));
}
