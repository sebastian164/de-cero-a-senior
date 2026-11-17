// ---- react-next/nivel-05-arquitectura/03-estructura-estado-y-arquitectura-limpia/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
// app/(privado)/pedidos/page.tsx — delgada
import { ListaDePedidos, listarPedidosDelUsuario } from '@/funcionalidades/pedidos';

export default async function Pagina({ searchParams }: { searchParams: Promise<{ estado?: string }> }) {
  const { estado } = await searchParams;
  return <ListaDePedidos pedidos={await listarPedidosDelUsuario({ estado })} />;
}
