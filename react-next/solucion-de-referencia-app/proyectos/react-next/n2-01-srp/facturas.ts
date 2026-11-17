// ---- react-next/nivel-02-solid/01-srp/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import 'server-only';
import { FacturaNoEncontrada, aFactura, API, TARIFA_IVA, type Factura } from './contexto';

// facturas/dominio.ts — reglas puras
export const subtotal = (f: Factura) => f.lineas.reduce((s, l) => s + l.valor, 0);
export const total = (f: Factura) => subtotal(f) * (1 + TARIFA_IVA);

// facturas/datos.ts — servidor y traducción
export async function obtenerFactura(id: string): Promise<Factura> {
  const respuesta = await fetch(`${API}/facturas/${id}`);
  if (!respuesta.ok) throw new FacturaNoEncontrada(id);
  return aFactura(await respuesta.json());
}

// facturas/exportar.ts — una sola tarea
export function aCsv(factura: Factura): string {
  return ['descripcion,valor', ...factura.lineas.map((l) => `${l.descripcion},${l.valor}`)].join('\n');
}

export type { Factura };
