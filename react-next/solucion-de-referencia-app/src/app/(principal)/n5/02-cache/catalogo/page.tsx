import { Suspense } from 'react';
import Link from 'next/link';
import { connection } from 'next/server';
import { listaPorTiempo, precioExacto } from './datos';

export const metadata = { title: 'nivel-05/02 · Catálogo' };

async function Lista() {
  const lista = await listaPorTiempo();
  return <ul data-testid="lista">{lista.map((a) => <li key={a.id}><Link href={`/n5/02-cache/catalogo/${a.id}`}>{a.nombre}</Link>: <span data-testid={`lista-${a.id}`}>{a.precio}</span></li>)}</ul>;
}
async function Exacto() {
  await connection();
  const a = await precioExacto('1');
  return <p>Sin caché, precio exacto del café: <span data-testid="exacto">{a?.precio}</span></p>;
}

export default function Pagina() {
  return (
    <main>
      <h1>Catálogo con tres estrategias</h1>
      <h2>Lista, por tiempo</h2>
      <Suspense fallback={<p>Cargando lista…</p>}><Lista /></Suspense>
      <h2>Exacto</h2>
      <Suspense fallback={<p>Cargando…</p>}><Exacto /></Suspense>
    </main>
  );
}
