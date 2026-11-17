import PaginaProducto from '@/react-next/n5-01-servidor-cliente/antes/pagina-producto';

export const metadata = { title: 'Antes: página de cliente' };

// Next.js 16 entrega params como promesa a las páginas de servidor; el componente "Antes" del README
// (de cliente, con useEffect) recibe el objeto ya resuelto.
export default async function Pagina({ params }: { params: Promise<{ id: string }> }) {
  return <PaginaProducto params={await params} />;
}
