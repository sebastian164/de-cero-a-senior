// Reglas comerciales y tributarias. Sin Angular: cambian cuando cambian Ventas o Contabilidad.
export type TipoCliente = 'minorista' | 'mayorista';
export interface LineaFactura { readonly descripcion: string; readonly cantidad: number; readonly valorUnitario: number; }
export interface Factura {
  readonly id: string; readonly cliente: string; readonly tipoCliente: TipoCliente;
  readonly emitidaEn: Date; readonly lineas: readonly LineaFactura[];
}
export const TARIFA_IVA = 0.19;

export const valorLinea = (l: LineaFactura) => l.cantidad * l.valorUnitario;
export const subtotal = (f: Factura) => f.lineas.reduce((s, l) => s + valorLinea(l), 0);
export const descuentoComercial = (f: Factura) => {
  const s = subtotal(f);
  if (s <= 1_000_000) return 0;
  return s * (f.tipoCliente === 'mayorista' ? 0.1 : 0.05);
};
export const iva = (f: Factura) => Math.round((subtotal(f) - descuentoComercial(f)) * TARIFA_IVA);
export const total = (f: Factura) => subtotal(f) - descuentoComercial(f) + iva(f);
export const sePuedeEnviar = (f: Factura, correo: string) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(correo) && f.lineas.length > 0;
