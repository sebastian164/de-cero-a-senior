'use client';
import { useActionState } from 'react';
import { cancelarDesdeFormulario } from './acciones';

export function FormularioCancelar({ ids }: { ids: readonly string[] }) {
  const [mensaje, accion, pendiente] = useActionState(cancelarDesdeFormulario, '');
  return (
    <form action={accion}>
      <label>Pedido <select name="pedidoId">{ids.map((id) => <option key={id}>{id}</option>)}</select></label>{' '}
      <button type="submit" disabled={pendiente}>Cancelar pedido</button>
      {mensaje && <p role="status">{mensaje}</p>}
    </form>
  );
}
