'use client';
import { useEffect, useState } from 'react';
function Factura({ id }: { id: string }) {
  const [factura, setFactura] = useState<any>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    fetch(`/api/facturas/${id}`).then((r) => r.json()).then((dto) => {
      setFactura({ ...dto, fecha: new Date(dto.fecha_emision) });
      setCargando(false);
    });
  }, [id]);

  if (cargando) return <p>Cargando…</p>;
  const subtotal = factura.lineas.reduce((s: number, l: any) => s + l.valor, 0);
  const total = subtotal * 1.19;                                      // regla tributaria en el JSX

  function exportarCsv() { /* arma el CSV y lo descarga */ }

  return (
    <div>
      <h1>Factura {factura.numero}</h1>
      <p>{factura.fecha.getDate()}/{factura.fecha.getMonth() + 1}/{factura.fecha.getFullYear()}</p>
      <p>Total: {total}</p>
      <button onClick={exportarCsv}>Exportar</button>
    </div>
  );
}

export { Factura };
