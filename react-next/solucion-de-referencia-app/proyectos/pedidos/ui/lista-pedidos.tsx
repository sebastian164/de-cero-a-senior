// Contexto: '@/pedidos/ui/lista-pedidos'. Componente de servidor: solo el botón es de cliente.
import { BotonCancelar } from './boton-cancelar';
import type { Pedido } from '../servidor';

export function ListaPedidos({ pedidos }: { pedidos: readonly Pedido[] }) {
  if (pedidos.length === 0) return <p>No hay pedidos activos.</p>;
  return (
    <ul>
      {pedidos.map((p) => (
        <li key={p.id}>
          {p.cliente} — {p.valor} <BotonCancelar pedidoId={p.id} />
        </li>
      ))}
    </ul>
  );
}
