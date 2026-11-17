import Link from 'next/link';
import { IngresoDemo } from './ingreso-demo';

export const metadata = { title: 'nivel-05/03 · Arquitectura' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-05/03 · Estructura, estado y arquitectura limpia</h1>
      <p>Los pedidos viven en PostgreSQL. Ingresa con un usuario de demostración para ver los suyos.</p>
      <IngresoDemo />
      <p><Link href="/n5/03-arquitectura/pedidos">Pedidos del usuario (ruta delgada del README)</Link></p>
    </main>
  );
}
