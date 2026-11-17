// Contexto: reglas puras que el README de clean code deja "en el dominio".
export interface LineaResumen { readonly producto: string; readonly precio: number; readonly cantidad: number }
export interface PedidoResumen {
  readonly id: string; readonly numero: string; readonly estado: 'pendiente' | 'pagado' | 'enviado' | 'cancelado';
  readonly creadoEn: Date; readonly lineas: readonly LineaResumen[];
}
export const calcularTotal = (p: PedidoResumen) => p.lineas.reduce((s, l) => s + l.precio * l.cantidad, 0);
export const sePuedeCancelar = (p: PedidoResumen) => p.estado === 'pendiente' || p.estado === 'pagado';
