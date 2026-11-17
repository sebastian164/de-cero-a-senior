'use client';
// Contexto (nivel-05/03): la lista que compone la página. Llama a la acción (adaptador de entrada) y muestra el resultado.
import { useState, useTransition } from 'react';
import { cancelarPedido } from '../acciones/cancelar';
import type { Pedido } from '../dominio/pedido';
import { FiltroDeEstado } from './filtro-de-estado';

const moneda = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

export function ListaDePedidos({ pedidos }: { pedidos: readonly Pedido[] }) {
  const [mensaje, setMensaje] = useState('');
  const [pendiente, iniciar] = useTransition();
  return (
    <div>
      <FiltroDeEstado />
      {pedidos.length === 0 ? <p>No hay pedidos con ese filtro.</p> : (
        <ul>
          {pedidos.map((p) => (
            <li key={p.id}>
              {p.id} · {p.estado} · {moneda.format(p.total)}{' '}
              {p.estado !== 'cancelado' && (
                <button type="button" disabled={pendiente} onClick={() => iniciar(async () => {
                  const r = await cancelarPedido(p.id);
                  setMensaje(r.ok ? `Pedido ${p.id} cancelado` : r.error);
                })}>Cancelar {p.id}</button>
              )}
            </li>
          ))}
        </ul>
      )}
      <p role="status">{mensaje}</p>
    </div>
  );
}
