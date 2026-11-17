import Link from 'next/link';

export const metadata = { title: 'nivel-05/01 · Servidor y cliente' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-05/01 · Componentes de servidor y de cliente</h1>
      <ul>
        <li><Link href="/n5/01-servidor-cliente/antes/1">Antes: página totalmente de cliente (README)</Link></li>
        <li><Link href="/n5/01-servidor-cliente/productos/1">Después: servidor por defecto, cliente en las hojas (README)</Link></li>
        <li><Link href="/n5/01-servidor-cliente/fuga">Taller punto 2: el registro completo cruza la frontera</Link></li>
        <li><Link href="/n5/01-servidor-cliente/proyectada">Taller punto 2: proyección en el servidor</Link></li>
      </ul>
    </main>
  );
}
