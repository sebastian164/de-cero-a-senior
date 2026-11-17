'use client';
// Taller nivel-02/01 — el componente de partida: más de 200 líneas y cinco actores (ver ANALISIS.md).
// Se conserva para comparar su comportamiento con la versión separada en ../despues/.
/* eslint-disable react-hooks/exhaustive-deps */
import { useEffect, useState } from 'react';

export function EditorFacturaAntes({ id }: { id: string }) {
  const [factura, setFactura] = useState<any>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [subtotal, setSubtotal] = useState(0);
  const [iva, setIva] = useState(0);
  const [total, setTotal] = useState(0);
  const [descuento, setDescuento] = useState(0);
  const [nuevaDescripcion, setNuevaDescripcion] = useState('');
  const [nuevoValor, setNuevoValor] = useState('');
  const [errorLinea, setErrorLinea] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [mensaje, setMensaje] = useState<string | null>(null);
  const [nota, setNota] = useState('');

  useEffect(() => {
    setCargando(true);
    fetch(`/api/n2-01/facturas/${id}`)
      .then((r) => {
        if (!r.ok) throw new Error('No encontrada');
        return r.json();
      })
      .then((dto) => {
        setFactura({
          numero: dto.numero,
          cliente: dto.cliente_nombre,
          tipoCliente: dto.cliente_tipo,
          fecha: new Date(dto.fecha_emision),
          lineas: dto.lineas.map((l: any) => ({ descripcion: l.desc, valor: l.valor_centavos / 100 })),
        });
        setCargando(false);
      })
      .catch(() => {
        setError('No se pudo cargar la factura');
        setCargando(false);
      });
  }, [id]);

  useEffect(() => {
    if (!factura) return;
    const s = factura.lineas.reduce((acc: number, l: any) => acc + l.valor, 0);
    setSubtotal(s);
  }, [factura]);

  useEffect(() => {
    if (!factura) return;
    // Regla comercial: mayoristas tienen 5 % si el subtotal supera 1.000.000; frecuentes, 2 % siempre.
    let d = 0;
    if (factura.tipoCliente === 'mayorista' && subtotal > 1_000_000) d = subtotal * 0.05;
    if (factura.tipoCliente === 'frecuente') d = subtotal * 0.02;
    setDescuento(Math.round(d));
  }, [subtotal, factura]);

  useEffect(() => {
    // Regla tributaria: IVA del 19 % sobre la base con descuento; exento si el cliente es 'exento'.
    if (!factura) return;
    const base = subtotal - descuento;
    const i = factura.tipoCliente === 'exento' ? 0 : Math.round(base * 0.19);
    setIva(i);
    setTotal(base + i);
  }, [subtotal, descuento, factura]);

  function agregarLinea() {
    setErrorLinea(null);
    if (nuevaDescripcion.trim().length < 3) {
      setErrorLinea('La descripción debe tener al menos 3 caracteres');
      return;
    }
    const valor = Number(nuevoValor.replace(/\./g, '').replace(',', '.'));
    if (Number.isNaN(valor) || valor <= 0) {
      setErrorLinea('El valor debe ser un número positivo');
      return;
    }
    if (factura.lineas.length >= 10) {
      setErrorLinea('Una factura admite máximo 10 líneas');
      return;
    }
    setFactura({ ...factura, lineas: [...factura.lineas, { descripcion: nuevaDescripcion.trim(), valor }] });
    setNuevaDescripcion('');
    setNuevoValor('');
  }

  function quitarLinea(indice: number) {
    setFactura({ ...factura, lineas: factura.lineas.filter((_: any, i: number) => i !== indice) });
  }

  async function guardar() {
    setGuardando(true);
    setMensaje(null);
    try {
      const r = await fetch(`/api/n2-01/facturas/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          numero: factura.numero,
          lineas: factura.lineas.map((l: any) => ({ desc: l.descripcion, valor_centavos: Math.round(l.valor * 100) })),
        }),
      });
      if (!r.ok) throw new Error();
      setMensaje('Factura guardada');
    } catch {
      setMensaje('No se pudo guardar');
    } finally {
      setGuardando(false);
    }
  }

  function exportarCsv() {
    const filas = ['descripcion;valor'];
    for (const l of factura.lineas) filas.push(`${l.descripcion};${l.valor}`);
    filas.push(`subtotal;${subtotal}`);
    filas.push(`descuento;${descuento}`);
    filas.push(`iva;${iva}`);
    filas.push(`total;${total}`);
    const blob = new Blob([filas.join('\n')], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `factura-${factura.numero}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  if (cargando) return <p>Cargando…</p>;
  if (error) return <p role="alert">{error}</p>;

  return (
    <div>
      <h3>Factura {factura.numero}</h3>
      <p>
        Cliente: {factura.cliente} ({factura.tipoCliente}) · Fecha:{' '}
        {factura.fecha.getUTCDate()}/{factura.fecha.getUTCMonth() + 1}/{factura.fecha.getUTCFullYear()}
      </p>
      <table>
        <thead>
          <tr>
            <th>Descripción</th>
            <th>Valor</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {factura.lineas.map((l: any, i: number) => (
            <tr key={i}>
              <td>{l.descripcion}</td>
              <td>${l.valor.toLocaleString('es-CO')}</td>
              <td>
                <button type="button" onClick={() => quitarLinea(i)}>
                  Quitar
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div>
        <label>
          Descripción
          <input value={nuevaDescripcion} onChange={(e) => setNuevaDescripcion(e.target.value)} />
        </label>
        <label>
          Valor
          <input value={nuevoValor} onChange={(e) => setNuevoValor(e.target.value)} />
        </label>
        <button type="button" onClick={agregarLinea}>
          Agregar línea
        </button>
        {errorLinea && <p role="alert">{errorLinea}</p>}
      </div>
      <dl>
        <dt>Subtotal</dt>
        <dd>${subtotal.toLocaleString('es-CO')}</dd>
        <dt>Descuento</dt>
        <dd>${descuento.toLocaleString('es-CO')}</dd>
        <dt>IVA</dt>
        <dd>${iva.toLocaleString('es-CO')}</dd>
        <dt>Total</dt>
        <dd>${total.toLocaleString('es-CO')}</dd>
      </dl>
      <button type="button" onClick={guardar} disabled={guardando}>
        {guardando ? 'Guardando…' : 'Guardar'}
      </button>{' '}
      <button type="button" onClick={exportarCsv}>
        Exportar CSV
      </button>
      {mensaje && <p role="status">{mensaje}</p>}
      <div>
        <label>
          Nota interna
          <textarea
            value={nota}
            maxLength={200}
            onChange={(e) => setNota(e.target.value)}
          />
        </label>
        <small>{nota.length}/200</small>
      </div>
    </div>
  );
}
