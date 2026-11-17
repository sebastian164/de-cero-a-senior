// Contexto que el README de SRP da por supuesto: modelo, DTO, adaptador y error.
export const TARIFA_IVA = 0.19;
// La API de facturas es la propia aplicación (src/app/api/n2-01/facturas/[id]/route.ts).
export const API = process.env.API_FACTURAS ?? `http://127.0.0.1:${process.env.PORT ?? 49510}/api/n2-01`;

export interface LineaFactura { readonly descripcion: string; readonly valor: number }
export interface Factura { readonly numero: string; readonly fecha: Date; readonly lineas: readonly LineaFactura[] }

export class FacturaNoEncontrada extends Error {
  constructor(readonly id: string) { super(`Factura ${id} no encontrada`); }
}

interface FacturaDto { numero: string; fecha_emision: string; lineas: { desc: string; valor_centavos: number }[] }
export const aFactura = (dto: FacturaDto): Factura => ({
  numero: dto.numero,
  fecha: new Date(dto.fecha_emision),
  lineas: dto.lineas.map((l) => ({ descripcion: l.desc, valor: l.valor_centavos / 100 })),
});
