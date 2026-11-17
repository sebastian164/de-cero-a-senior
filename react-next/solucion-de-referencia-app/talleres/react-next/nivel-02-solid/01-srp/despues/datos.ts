// Hablar con el servidor y traducir su formato (DTO) al modelo. Ningún componente conoce URLs ni DTO.
import type { Factura, TipoCliente } from './dominio';

interface FacturaDto {
  numero: string; cliente_nombre: string; cliente_tipo: TipoCliente; fecha_emision: string;
  lineas: { desc: string; valor_centavos: number }[];
}

export const aFactura = (dto: FacturaDto): Factura => ({
  numero: dto.numero,
  cliente: dto.cliente_nombre,
  tipoCliente: dto.cliente_tipo,
  fecha: new Date(dto.fecha_emision),
  lineas: dto.lineas.map((l) => ({ descripcion: l.desc, valor: l.valor_centavos / 100 })),
});

export const aDto = (f: Factura) => ({
  numero: f.numero,
  lineas: f.lineas.map((l) => ({ desc: l.descripcion, valor_centavos: Math.round(l.valor * 100) })),
});

export async function obtenerFactura(id: string, signal?: AbortSignal): Promise<Factura> {
  const r = await fetch(`/api/n2-01/facturas/${id}`, { signal });
  if (!r.ok) throw new Error('No encontrada');
  return aFactura(await r.json());
}

export async function guardarFactura(id: string, f: Factura): Promise<void> {
  const r = await fetch(`/api/n2-01/facturas/${id}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(aDto(f)),
  });
  if (!r.ok) throw new Error('No se pudo guardar');
}
