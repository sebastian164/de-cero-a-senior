'use client';
// Presentación y coordinación de la pantalla. Cambiar el IVA o el descuento no toca este archivo.
import { useState } from 'react';
import { totales, type Factura } from './dominio';
import { aCsv } from './exportar';
import { useEditorFactura } from './use-editor-factura';

const moneda = (v: number) => `$${v.toLocaleString('es-CO')}`;
const fecha = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'numeric', year: 'numeric', timeZone: 'UTC' });

export function EditorFactura({ id }: { id: string }) {
  const { carga, errorLinea, guardado, agregar, quitar, guardar } = useEditorFactura(id);
  if (carga.tipo === 'cargando') return <p>Cargando…</p>;
  if (carga.tipo === 'error') return <p role="alert">No se pudo cargar la factura</p>;
  const { factura } = carga;
  return (
    <div>
      <h3>Factura {factura.numero}</h3>
      <p>Cliente: {factura.cliente} ({factura.tipoCliente}) · Fecha: {fecha.format(factura.fecha)}</p>
      <TablaLineas factura={factura} onQuitar={quitar} />
      <NuevaLinea onAgregar={agregar} error={errorLinea} />
      <ResumenTotales factura={factura} />
      <button type="button" onClick={guardar} disabled={guardado === 'guardando'}>{guardado === 'guardando' ? 'Guardando…' : 'Guardar'}</button>{' '}
      <BotonDescargar nombre={`factura-${factura.numero}.csv`} contenido={() => aCsv(factura)} />
      {guardado === 'ok' && <p role="status">Factura guardada</p>}
      {guardado === 'error' && <p role="status">No se pudo guardar</p>}
      <NotaInterna />
    </div>
  );
}

function TablaLineas({ factura, onQuitar }: { factura: Factura; onQuitar: (i: number) => void }) {
  return (
    <table>
      <thead><tr><th>Descripción</th><th>Valor</th><th /></tr></thead>
      <tbody>
        {factura.lineas.map((l, i) => (
          <tr key={`${i}-${l.descripcion}`}>
            <td>{l.descripcion}</td><td>{moneda(l.valor)}</td>
            <td><button type="button" onClick={() => onQuitar(i)}>Quitar</button></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function NuevaLinea({ onAgregar, error }: { onAgregar: (d: string, v: string) => boolean; error: string | null }) {
  const [descripcion, setDescripcion] = useState('');
  const [valor, setValor] = useState('');
  function agregar() {
    if (onAgregar(descripcion, valor)) { setDescripcion(''); setValor(''); }
  }
  return (
    <div>
      <label>Descripción<input value={descripcion} onChange={(e) => setDescripcion(e.target.value)} /></label>
      <label>Valor<input value={valor} onChange={(e) => setValor(e.target.value)} /></label>
      <button type="button" onClick={agregar}>Agregar línea</button>
      {error && <p role="alert">{error}</p>}
    </div>
  );
}

function ResumenTotales({ factura }: { factura: Factura }) {
  const t = totales(factura);
  return (
    <dl>
      <dt>Subtotal</dt><dd>{moneda(t.subtotal)}</dd>
      <dt>Descuento</dt><dd>{moneda(t.descuento)}</dd>
      <dt>IVA</dt><dd>{moneda(t.iva)}</dd>
      <dt>Total</dt><dd>{moneda(t.total)}</dd>
    </dl>
  );
}

function BotonDescargar({ nombre, contenido }: { nombre: string; contenido: () => string }) {
  function descargar() {
    const url = URL.createObjectURL(new Blob([contenido()], { type: 'text/csv' }));
    const a = document.createElement('a');
    a.href = url; a.download = nombre; a.click();
    URL.revokeObjectURL(url);
  }
  return <button type="button" onClick={descargar}>Exportar CSV</button>;
}

function NotaInterna() {
  const [nota, setNota] = useState('');
  return (
    <div>
      <label>Nota interna<textarea value={nota} maxLength={200} onChange={(e) => setNota(e.target.value)} /></label>
      <small>{nota.length}/200</small>
    </div>
  );
}
