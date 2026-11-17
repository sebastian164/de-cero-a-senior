import { Suspense } from 'react';
import { esperar, PRODUCTOS } from '../datos';

async function Resultados({ q }: { q: string }) {
  await esperar(400);
  const r = PRODUCTOS.filter((p) => p.nombre.toLowerCase().includes(q.toLowerCase()));
  return <ul>{r.map((p) => <li key={p.id}>{p.nombre}</li>)}</ul>;
}
export default async function Pagina({ searchParams }: { searchParams: Promise<{ q?: string }> }) {
  const { q = '' } = await searchParams;
  return <main><h1>Búsqueda: {q}</h1><Suspense fallback={<p>Buscando…</p>}><Resultados q={q} /></Suspense></main>;
}
