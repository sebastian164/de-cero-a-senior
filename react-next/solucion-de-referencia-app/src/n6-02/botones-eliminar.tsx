'use client';
import { useState, useTransition } from 'react';
import { eliminarPedido } from '@/react-next/n6-02-seguridad/eliminar-despues';
import { eliminarPedido as eliminarPedidoVulnerable } from '@/react-next/n6-02-seguridad/eliminar-antes';
import { useRouter } from 'next/navigation';

export function BotonEliminar({ id, vulnerable = false }: { id: string; vulnerable?: boolean }) {
  const [mensaje, setMensaje] = useState('');
  const [pendiente, iniciar] = useTransition();
  const router = useRouter();
  return (
    <>
      <button type="button" disabled={pendiente} onClick={() => iniciar(async () => {
        if (vulnerable) { await eliminarPedidoVulnerable(id); setMensaje('Eliminado (sin comprobar nada)'); } else {
          const r = await eliminarPedido(id);
          setMensaje(r.ok ? 'Eliminado' : r.error);
        }
        router.refresh();
      })}>{vulnerable ? 'Eliminar (acción vulnerable)' : 'Eliminar'} {id.slice(0, 4)}</button>
      <span role="status"> {mensaje}</span>
    </>
  );
}
