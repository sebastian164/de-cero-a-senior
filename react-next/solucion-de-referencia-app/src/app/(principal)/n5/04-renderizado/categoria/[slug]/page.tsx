import { Suspense } from 'react';
import { notFound } from 'next/navigation';
import { listarCategoria } from '../../datos';

export function generateStaticParams() { return [{ slug: 'bebidas' }, { slug: 'despensa' }]; }

async function Lista({ slug }: { slug: string }) {
  const productos = await listarCategoria(slug);
  if (productos.length === 0) notFound();
  return <ul>{productos.map((p) => <li key={p.id}>{p.nombre}</li>)}</ul>;
}
export default async function Pagina({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  return <main><h1>Categoría {slug}</h1><Suspense fallback={<p>Cargando…</p>}><Lista slug={slug} /></Suspense></main>;
}
