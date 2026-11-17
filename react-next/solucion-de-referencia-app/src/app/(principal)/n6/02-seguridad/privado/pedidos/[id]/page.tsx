import { notFound } from 'next/navigation';
import { obtenerPedido } from '@/react-next/n6-02-seguridad/acceso';

export default async function Pagina({ params }: { params: Promise<{ id: string }> }) {
  const pedido = await obtenerPedido((await params).id);        // la capa de acceso decide: ajeno e inexistente son lo mismo
  if (!pedido) notFound();
  return (
    <main>
      <h1>Pedido {pedido.id.slice(0, 4)}</h1>
      <p>Estado: {pedido.estado} · Total: {pedido.total}</p>
      <ul>{pedido.lineas.map((l) => <li key={l.producto}>{l.producto} × {l.cantidad}</li>)}</ul>
    </main>
  );
}
