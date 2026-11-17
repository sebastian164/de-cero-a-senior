'use client';
// Taller nivel-04/01, puntos 1 a 4: el mismo panel limpio.
// - Estado mínimo: pedidos y filtro. Total, pendientes y filtrados se calculan al renderizar.
// - El único efecto sincroniza con algo externo (la carga) y se limpia.
// - Carga como unión discriminada: no hay combinaciones imposibles.
// - Condiciones y manejadores con nombre; retornos tempranos; claves estables; `> 0` en lugar de un número suelto.
import { useEffect, useState } from 'react';
import { cobrarPendientes, filtrarPorCliente, pendientesDe, puedeCobrar, sumarTotal, type Pedido } from './dominio';

type Carga = { tipo: 'cargando' } | { tipo: 'error'; mensaje: string } | { tipo: 'lista'; pedidos: readonly Pedido[] };

export function PanelPedidos({ cargar, usuario }: { cargar: () => Promise<Pedido[]>; usuario: { rol: string } | null }) {
  // 1. Estado y efectos
  const [carga, setCarga] = useState<Carga>({ tipo: 'cargando' });
  const [filtro, setFiltro] = useState('');
  useEffect(() => {
    let vigente = true;
    cargar()
      .then((pedidos) => { if (vigente) setCarga({ tipo: 'lista', pedidos }); })
      .catch(() => { if (vigente) setCarga({ tipo: 'error', mensaje: 'No se pudieron cargar' }); });
    return () => { vigente = false; };
  }, [cargar]);

  // 2. Retornos tempranos
  if (carga.tipo === 'cargando') return <p>Cargando…</p>;
  if (carga.tipo === 'error') return <p role="alert">{carga.mensaje}</p>;

  // 3. Valores derivados
  const { pedidos } = carga;
  const pendientes = pendientesDe(pedidos);
  const mostrarCobro = puedeCobrar(usuario, pendientes);

  // 4. Manejadores con nombre
  function cobrar() {
    setCarga({ tipo: 'lista', pedidos: cobrarPendientes(pedidos) });
  }

  // 5. Interfaz
  return (
    <div>
      <label>Cliente <input value={filtro} onChange={(e) => setFiltro(e.target.value)} /></label>
      {pendientes.length > 0 && <p>Pendientes por cobrar</p>}
      <ul>{filtrarPorCliente(pedidos, filtro).map((p) => <li key={p.id}>{p.cliente}: {p.total}</li>)}</ul>
      <p>Total: {sumarTotal(pedidos)}</p>
      {mostrarCobro && <button type="button" onClick={cobrar}>Cobrar pendientes</button>}
    </div>
  );
}
