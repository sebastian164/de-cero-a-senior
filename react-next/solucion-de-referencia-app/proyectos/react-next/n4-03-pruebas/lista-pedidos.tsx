// Contexto: ListaPedidos con onCancelar, la que prueba el README.
export interface PedidoLista { readonly id: string; readonly cliente: string; readonly total: number }

export function ListaPedidos({ pedidos, onCancelar }: { pedidos: readonly PedidoLista[]; onCancelar: (id: string) => void }) {
  return (
    <ul>
      {pedidos.map((p) => (
        <li key={p.id}>{p.cliente} · {p.total} <button type="button" onClick={() => onCancelar(p.id)}>Cancelar pedido de {p.cliente}</button></li>
      ))}
    </ul>
  );
}
