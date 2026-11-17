import Link from 'next/link';
import { redirect } from 'next/navigation';
import { obtenerUsuarioActual } from '@talleres/react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/sesion';
import { listarPedidosVisibles } from '@/react-next/n6-02-seguridad/acceso-lista';
import { BotonEliminar } from '@app/n6-02/botones-eliminar';

export const metadata = { title: 'Mis pedidos' };

export default async function Pagina() {
  const usuario = await obtenerUsuarioActual();
  if (!usuario) redirect('/n6/02-seguridad?retorno=/n6/02-seguridad/privado');
  const pedidos = await listarPedidosVisibles();
  return (
    <main>
      <h1>Pedidos de {usuario.nombre}</h1>
      <ul>
        {pedidos.map((p) => (
          <li key={p.id}>
            <Link href={`/n6/02-seguridad/privado/pedidos/${p.id}`}>Pedido {p.id.slice(0, 4)}</Link> · {p.estado} · {p.total}{' '}
            <BotonEliminar id={p.id} /> <BotonEliminar id={p.id} vulnerable />
          </li>
        ))}
      </ul>
    </main>
  );
}
