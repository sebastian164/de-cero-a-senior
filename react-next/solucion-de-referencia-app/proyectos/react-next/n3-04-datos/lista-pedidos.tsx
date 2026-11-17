// Contexto: presentación de la lista (sesión de capa de datos).
import type { Pedido } from './pedidos/datos/esquema';

const moneda = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

export function ListaPedidos({ pedidos }: { pedidos: readonly Pedido[] }) {
  if (pedidos.length === 0) return <p>Sin pedidos.</p>;
  return <ul>{pedidos.map((p) => <li key={p.id}>#{p.id} · {p.cliente} · {p.estado} · {moneda.format(p.total)}</li>)}</ul>;
}
