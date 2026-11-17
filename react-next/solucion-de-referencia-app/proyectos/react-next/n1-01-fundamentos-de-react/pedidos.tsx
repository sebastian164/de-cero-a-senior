'use client';
// ---- react-next/nivel-01-fundamentos/01-fundamentos-de-react/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import { useState } from 'react';

interface Pedido {
  readonly id: string;
  readonly cliente: string;
  readonly valor: number;
}

const sumar = (pedidos: readonly Pedido[]) => pedidos.reduce((suma, p) => suma + p.valor, 0);

function PaginaPedidos() {
  const [pedidos, setPedidos] = useState<readonly Pedido[]>([]);
  const total = sumar(pedidos);                       // derivado: se calcula al renderizar

  function agregar(pedido: Pedido) {
    setPedidos((actuales) => [...actuales, pedido]);  // nuevo arreglo; forma funcional
  }

  function eliminar(id: string) {
    setPedidos((actuales) => actuales.filter((p) => p.id !== id));
  }

  return (
    <section>
      <ListaPedidos pedidos={pedidos} onEliminar={eliminar} />
      <p>Total: {total.toLocaleString('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })}</p>
    </section>
  );
}

interface ListaPedidosProps {
  readonly pedidos: readonly Pedido[];
  readonly onEliminar: (id: string) => void;
}

function ListaPedidos({ pedidos, onEliminar }: ListaPedidosProps) {
  if (pedidos.length === 0) return <p>No hay pedidos.</p>;

  return (
    <ul>
      {pedidos.map((pedido) => (
        <li key={pedido.id}>
          {pedido.cliente}
          <button type="button" onClick={() => onEliminar(pedido.id)}>Eliminar</button>
        </li>
      ))}
    </ul>
  );
}

export { PaginaPedidos, ListaPedidos, sumar };
export type { Pedido };
