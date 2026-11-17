// ---- react-next/nivel-05-arquitectura/02-datos-cache-y-mutaciones/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
'use client';

export function ListaPedidos({ pedidos }: { pedidos: readonly Pedido[] }) {
  const [visibles, quitarOptimista] = useOptimistic(pedidos, (actuales, id: string) =>
    actuales.filter((p) => p.id !== id));
  const [, iniciar] = useTransition();

  function cancelar(id: string) {
    iniciar(async () => {
      quitarOptimista(id);                              // se refleja de inmediato
      const resultado = await cancelarPedido(id);       // si falla, React vuelve al estado real
      if (!resultado.ok) avisar(resultado.error);
    });
  }
  return (
    <ul>
      {visibles.map((p) => (
        <li key={p.id}>{p.descripcion} · {p.estado} <button type="button" onClick={() => cancelar(p.id)}>Cancelar {p.descripcion}</button></li>
      ))}
    </ul>
  );
}

import { useOptimistic, useTransition } from 'react';
import { cancelarPedido } from './acciones';
import { avisar } from './avisos';
import type { Pedido } from './contexto';
