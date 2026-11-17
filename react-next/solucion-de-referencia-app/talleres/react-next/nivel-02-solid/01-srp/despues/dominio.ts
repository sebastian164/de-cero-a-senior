// Reglas puras: comerciales y tributarias. Sin React, sin red.
export type TipoCliente = 'normal' | 'frecuente' | 'mayorista' | 'exento';
export interface LineaFactura { readonly descripcion: string; readonly valor: number }
export interface Factura {
  readonly numero: string;
  readonly cliente: string;
  readonly tipoCliente: TipoCliente;
  readonly fecha: Date;
  readonly lineas: readonly LineaFactura[];
}
export interface Totales { readonly subtotal: number; readonly descuento: number; readonly iva: number; readonly total: number }

export const TARIFA_IVA = 0.19;
export const MAXIMO_LINEAS = 10;

export const subtotal = (f: Factura) => f.lineas.reduce((s, l) => s + l.valor, 0);

export function descuento(tipo: TipoCliente, base: number): number {
  if (tipo === 'mayorista' && base > 1_000_000) return Math.round(base * 0.05);
  if (tipo === 'frecuente') return Math.round(base * 0.02);
  return 0;
}

export function totales(f: Factura): Totales {
  const s = subtotal(f);
  const d = descuento(f.tipoCliente, s);
  const iva = f.tipoCliente === 'exento' ? 0 : Math.round((s - d) * TARIFA_IVA);
  return { subtotal: s, descuento: d, iva, total: s - d + iva };
}

export type ResultadoLinea = { ok: true; factura: Factura } | { ok: false; error: string };

export function agregarLinea(f: Factura, descripcion: string, valorTexto: string): ResultadoLinea {
  if (descripcion.trim().length < 3) return { ok: false, error: 'La descripción debe tener al menos 3 caracteres' };
  const valor = Number(valorTexto.replace(/\./g, '').replace(',', '.'));
  if (Number.isNaN(valor) || valor <= 0) return { ok: false, error: 'El valor debe ser un número positivo' };
  if (f.lineas.length >= MAXIMO_LINEAS) return { ok: false, error: `Una factura admite máximo ${MAXIMO_LINEAS} líneas` };
  return { ok: true, factura: { ...f, lineas: [...f.lineas, { descripcion: descripcion.trim(), valor }] } };
}

export const quitarLinea = (f: Factura, indice: number): Factura => ({ ...f, lineas: f.lineas.filter((_, i) => i !== indice) });
