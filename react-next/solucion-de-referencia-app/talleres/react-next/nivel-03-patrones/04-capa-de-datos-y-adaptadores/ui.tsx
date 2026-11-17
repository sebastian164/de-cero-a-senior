'use client';
// Puntos 4 y 5: lista con consulta, mutación optimista con reversa, y dos componentes que comparten la misma consulta.
import { useState } from 'react';
import { usePedidos, useCancelarPedido } from '@/react-next/n3-04-datos/pedidos/consultas';

const moneda = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

export function ListaCancelable() {
  const consulta = usePedidos({});
  const cancelar = useCancelarPedido();
  const [aviso, setAviso] = useState<string | null>(null);
  if (consulta.isPending) return <p>Cargando pedidos…</p>;
  if (consulta.isError) return <p role="alert">No se pudieron cargar los pedidos: {consulta.error.message}</p>;
  return (
    <div>
      <ul aria-label="pedidos">
        {consulta.data.map((p) => (
          <li key={p.id}>
            #{p.id} · {p.cliente} · {p.estado} · {moneda.format(p.total)}{' '}
            <button type="button" onClick={() => cancelar.mutate(p.id, {
              onSuccess: () => setAviso(`Pedido ${p.id} cancelado`),
              onError: () => setAviso(`No se pudo cancelar el pedido ${p.id}; se restauró la lista`),
            })}>Cancelar #{p.id}</button>
          </li>
        ))}
      </ul>
      {aviso && <p role="status">{aviso}</p>}
    </div>
  );
}

/** Otro componente en otra parte de la pantalla que pide la misma consulta: TanStack Query la deduplica por clave. */
export function ContadorPendientes() {
  const { data } = usePedidos({});
  return <p>Pendientes: {data ? data.filter((p) => p.estado === 'pendiente').length : '…'}</p>;
}
