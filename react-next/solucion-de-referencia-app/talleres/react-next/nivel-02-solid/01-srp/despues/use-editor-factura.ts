'use client';
// Lógica con estado de la pantalla: cargar, editar y guardar. Delega reglas al dominio y la red a datos.
import { useEffect, useState } from 'react';
import { agregarLinea, quitarLinea, type Factura } from './dominio';
import { guardarFactura, obtenerFactura } from './datos';

type Carga = { tipo: 'cargando' } | { tipo: 'error' } | { tipo: 'lista'; factura: Factura };

export function useEditorFactura(id: string) {
  const [carga, setCarga] = useState<Carga>({ tipo: 'cargando' });
  const [errorLinea, setErrorLinea] = useState<string | null>(null);
  const [guardado, setGuardado] = useState<'inactivo' | 'guardando' | 'ok' | 'error'>('inactivo');

  useEffect(() => {
    const control = new AbortController();
    obtenerFactura(id, control.signal)
      .then((factura) => setCarga({ tipo: 'lista', factura }))
      .catch((e: unknown) => { if (!(e instanceof DOMException && e.name === 'AbortError')) setCarga({ tipo: 'error' }); });
    return () => control.abort();
  }, [id]);

  function agregar(descripcion: string, valor: string): boolean {
    if (carga.tipo !== 'lista') return false;
    const r = agregarLinea(carga.factura, descripcion, valor);
    setErrorLinea(r.ok ? null : r.error);
    if (r.ok) setCarga({ tipo: 'lista', factura: r.factura });
    return r.ok;
  }

  function quitar(indice: number) {
    if (carga.tipo === 'lista') setCarga({ tipo: 'lista', factura: quitarLinea(carga.factura, indice) });
  }

  async function guardar() {
    if (carga.tipo !== 'lista') return;
    setGuardado('guardando');
    try {
      await guardarFactura(id, carga.factura);
      setGuardado('ok');
    } catch {
      setGuardado('error');
    }
  }

  return { carga, errorLinea, guardado, agregar, quitar, guardar };
}
