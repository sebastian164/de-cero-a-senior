// ---- react-next/nivel-01-fundamentos/03-fundamentos-de-next/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
// pedidos/ui/boton-cancelar.tsx — la única parte interactiva
'use client';

import { useTransition } from 'react';
import { cancelarPedido } from '@/pedidos/acciones';

export function BotonCancelar({ pedidoId }: { pedidoId: string }) {
  const [pendiente, iniciar] = useTransition();
  return (
    <button type="button" disabled={pendiente} onClick={() => iniciar(() => cancelarPedido(pedidoId))}>
      {pendiente ? 'Cancelando…' : 'Cancelar'}
    </button>
  );
}
