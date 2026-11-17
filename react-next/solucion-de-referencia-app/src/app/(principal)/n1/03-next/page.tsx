import Link from 'next/link';

export const metadata = { title: 'nivel-01/03 · Inicio' };

export default function Inicio() {
  return (
    <main>
      <h1>Biblioteca (taller nivel-01/03)</h1>
      <ul>
        <li><Link href="/n1/03-next/libros">Lista de libros (servidor)</Link></li>
        <li><Link href="/n1/03-next/libros-cliente">La misma lista, toda en el cliente</Link></li>
        <li><Link href="/pedidos">Ejemplo del README: /pedidos</Link></li>
      </ul>
    </main>
  );
}
