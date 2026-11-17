'use client';
// Contexto: BotonExportar del README de SRP. Usa aCsv sin importar el módulo de servidor.
import type { Factura } from './contexto';

const aCsv = (f: Factura) => ['descripcion,valor', ...f.lineas.map((l) => `${l.descripcion},${l.valor}`)].join('\n');

export function BotonExportar({ factura }: { factura: Factura }) {
  function exportar() {
    const url = URL.createObjectURL(new Blob([aCsv(factura)], { type: 'text/csv' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `factura-${factura.numero}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
  return <button type="button" onClick={exportar}>Exportar CSV</button>;
}
