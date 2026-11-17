// ---- react-next/nivel-05-arquitectura/03-estructura-estado-y-arquitectura-limpia/README.md (bloque 5, desde '// pedidos/dominio/pedido.ts'), copiado tal cual entre el contexto agregado ----
// Contexto: los tipos y la función de horas que el README da por supuestos (dominio puro, sin importaciones de framework).
export interface Pedido { readonly id: string; readonly clienteId: string; readonly estado: 'pendiente' | 'pagado' | 'cancelado'; readonly creadoEn: Date; readonly total: number }
export type Resultado = { ok: true } | { ok: false; error: string };
const horasEntre = (desde: Date, hasta: Date) => (hasta.getTime() - desde.getTime()) / 3_600_000;

// pedidos/dominio/pedido.ts — sin importaciones de framework
export const sePuedeCancelar = (pedido: Pedido, ahora: Date): boolean =>
  (pedido.estado === 'pendiente' || pedido.estado === 'pagado') && horasEntre(pedido.creadoEn, ahora) <= 24;
