'use client';
// ---- react-next/nivel-04-clean-code-y-testing/01-clean-code-en-react/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import { useState } from 'react';
import { calcularTotal, sePuedeCancelar, type PedidoResumen } from './dominio';

// Contexto: lo que el README da por supuesto.
interface ResumenPedidoProps { readonly pedido: PedidoResumen; readonly onCancelar: (id: string) => void }
const usePermisos = () => ({ puedeCancelar: true });
const formatoMoneda = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
function Confirmacion({ onAceptar, onCerrar }: { onAceptar: () => void; onCerrar: () => void }) {
  return <div role="alertdialog" aria-label="Confirmar cancelación"><button type="button" onClick={onAceptar}>Sí, cancelar</button> <button type="button" onClick={onCerrar}>No</button></div>;
}

export function ResumenPedido({ pedido, onCancelar }: ResumenPedidoProps) {
  // 1. Hooks: estado y contexto
  const [confirmando, setConfirmando] = useState(false);
  const { puedeCancelar } = usePermisos();

  // 2. Valores derivados
  const total = calcularTotal(pedido);
  const cancelable = puedeCancelar && sePuedeCancelar(pedido);

  // 3. Manejadores
  function confirmarCancelacion() {
    setConfirmando(false);
    onCancelar(pedido.id);
  }

  // 4. Retornos tempranos
  if (pedido.lineas.length === 0) return <p>El pedido no tiene productos.</p>;

  // 5. Interfaz
  return (
    <article>
      <h2>Pedido {pedido.numero}</h2>
      <p>{formatoMoneda.format(total)}</p>
      {cancelable && <button type="button" onClick={() => setConfirmando(true)}>Cancelar</button>}
      {confirmando && <Confirmacion onAceptar={confirmarCancelacion} onCerrar={() => setConfirmando(false)} />}
    </article>
  );
}
