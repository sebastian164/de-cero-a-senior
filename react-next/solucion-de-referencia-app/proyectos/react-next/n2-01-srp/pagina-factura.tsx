// ---- react-next/nivel-02-solid/01-srp/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
import { obtenerFactura, total } from './facturas';
import type { Factura } from './contexto';
import { BotonExportar } from './boton-exportar';

const formatoFecha = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeZone: 'America/Bogota' });
const formatoMoneda = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' });

// app/facturas/[id]/page.tsx — coordina (componente de servidor)
export default async function PaginaFactura({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const factura = await obtenerFactura(id);
  return <DetalleFactura factura={factura} />;
}

// facturas/ui/detalle-factura.tsx — presenta
export function DetalleFactura({ factura }: { factura: Factura }) {
  return (
    <article>
      <h1>Factura {factura.numero}</h1>
      <time dateTime={factura.fecha.toISOString()}>{formatoFecha.format(factura.fecha)}</time>
      <p>Total: {formatoMoneda.format(total(factura))}</p>
      <BotonExportar factura={factura} />
    </article>
  );
}
