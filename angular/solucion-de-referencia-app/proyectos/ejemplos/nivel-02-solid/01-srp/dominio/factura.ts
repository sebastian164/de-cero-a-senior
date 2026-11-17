// Tipos que el README no declara.
export interface Linea { readonly descripcion: string; readonly valor: number; }
export interface Factura { readonly id: string; readonly cliente: string; readonly fecha: Date; readonly lineas: readonly Linea[]; }
export const TARIFA_IVA = 0.19;

// dominio/factura.ts — reglas puras
export const subtotal = (f: Factura) => f.lineas.reduce((s, l) => s + l.valor, 0);
export const iva = (f: Factura) => subtotal(f) * TARIFA_IVA;
export const total = (f: Factura) => subtotal(f) + iva(f);
