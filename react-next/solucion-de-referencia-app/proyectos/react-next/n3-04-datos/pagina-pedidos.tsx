// ---- react-next/nivel-03-patrones/04-capa-de-datos-y-adaptadores/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import { listarPedidos, type FiltroPedidos } from './pedidos/datos/esquema';
import { ListaPedidos } from './lista-pedidos';

export default async function PaginaPedidos({ searchParams }: { searchParams: Promise<FiltroPedidos> }) {
  const pedidos = await listarPedidos(await searchParams);
  return <ListaPedidos pedidos={pedidos} />;
}
