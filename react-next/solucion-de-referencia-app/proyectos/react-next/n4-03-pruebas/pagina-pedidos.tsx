'use client';
// Contexto: PaginaPedidos de cliente que consulta /api/pedidos con la capa de datos del nivel-03/04 (esquema + adaptador).
import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { PedidoDto, aPedido } from '../n3-04-datos/pedidos/datos/esquema';

async function listar(signal: AbortSignal) {
  const r = await fetch('/api/pedidos', { signal });
  if (!r.ok) throw new Error(`Error ${r.status}`);
  return z.array(PedidoDto).parse(await r.json()).map(aPedido);
}

export function PaginaPedidos() {
  const consulta = useQuery({ queryKey: ['pedidos-prueba'], queryFn: ({ signal }) => listar(signal) });
  if (consulta.isPending) return <p>Cargando…</p>;
  if (consulta.isError) {
    return (
      <div role="alert">
        No se pudieron cargar los pedidos. <button type="button" onClick={() => consulta.refetch()}>Reintentar</button>
      </div>
    );
  }
  return <ul>{consulta.data.map((p) => <li key={p.id}>{p.cliente} · {p.estado}</li>)}</ul>;
}
