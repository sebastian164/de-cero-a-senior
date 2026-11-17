import { Suspense } from 'react';
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { obtenerFicha } from '../../datos';

export function generateStaticParams() { return [{ id: '1' }, { id: '2' }]; }

export async function generateMetadata({ params }: { params: Promise<{ id: string }> }): Promise<Metadata> {
  const ficha = await obtenerFicha((await params).id);
  return ficha ? { title: ficha.nombre, description: `${ficha.nombre} a ${ficha.precio} pesos`, alternates: { canonical: `/n5/04-renderizado/producto/${ficha.id}` } } : { title: 'No encontrado' };
}
async function Ficha({ id }: { id: string }) {
  const ficha = await obtenerFicha(id);
  if (!ficha) notFound();
  return <p>{ficha.nombre}: <span data-testid="precio">{ficha.precio}</span></p>;
}
export default async function Pagina({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <main><h1>Ficha de producto</h1><Suspense fallback={<p>Cargando…</p>}><Ficha id={id} /></Suspense></main>;
}
