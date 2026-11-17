import { useState } from 'react';
// Apoyo de la comprobación (no está en el README): expone lo que el bloque guarda en estado.
export const expuesto: { agregar?: (p: any) => void; pedidos?: any[] } = {};

function Pedidos() {
  const [pedidos, setPedidos] = useState<any[]>([]);
  const [total, setTotal] = useState(0);              // derivado: no debería ser estado

  function agregar(pedido: any) {
    pedidos.push(pedido);                             // modifica el estado en el sitio
    setPedidos(pedidos);                              // misma referencia: React no vuelve a pintar
    setTotal(total + pedido.valor);                   // y hay que acordarse de sincronizarlo
  }

  expuesto.agregar = agregar; expuesto.pedidos = pedidos;
  return (
    <div>
      {pedidos.map((p, i) => (
        <div key={i} onClick={() => alert(p.id)}>     {/* índice como clave; div clicable */}
          {p.cliente} - {p.valor}
        </div>
      ))}
      <p>Total: {total}</p>
    </div>
  );
}

export { Pedidos };
