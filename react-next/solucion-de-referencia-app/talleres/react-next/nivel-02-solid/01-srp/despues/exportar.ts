// Una sola tarea: convertir la factura a CSV. Pura; la descarga es asunto de la interfaz.
import { totales, type Factura } from './dominio';

export function aCsv(f: Factura): string {
  const t = totales(f);
  return ['descripcion;valor', ...f.lineas.map((l) => `${l.descripcion};${l.valor}`),
    `subtotal;${t.subtotal}`, `descuento;${t.descuento}`, `iva;${t.iva}`, `total;${t.total}`].join('\n');
}
