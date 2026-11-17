// ---- react-next/nivel-01-fundamentos/03-fundamentos-de-next/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
// app/pedidos/page.tsx — componente de servidor
import { listarPedidos } from '@/pedidos/datos';
import { ListaPedidos } from '@/pedidos/ui/lista-pedidos';

export default async function PaginaPedidos() {
  const pedidos = await listarPedidos();          // se ejecuta en el servidor
  return (
    <main>
      <h1>Pedidos</h1>
      <ListaPedidos pedidos={pedidos} />
    </main>
  );
}
