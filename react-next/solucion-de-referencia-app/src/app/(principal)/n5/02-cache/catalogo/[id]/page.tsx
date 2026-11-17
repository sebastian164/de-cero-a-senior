import { Suspense } from 'react';
import { notFound } from 'next/navigation';
import { detallePorEtiqueta } from '../datos';
import { invalidarConUpdateTag } from './acciones';

export function generateStaticParams() { return [{ id: '1' }, { id: '2' }]; }

async function Detalle({ id }: { id: string }) {
  const a = await detallePorEtiqueta(id);
  if (!a) notFound();
  return <p>{a.nombre}: <span data-testid="detalle">{a.precio}</span></p>;
}
export default async function Pagina({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <main><h1>Detalle, por etiqueta</h1><Suspense fallback={<p>Cargando…</p>}><Detalle id={id} /></Suspense><form action={invalidarConUpdateTag}><input type="hidden" name="id" value={id} /><button type="submit">Invalidar con updateTag</button></form></main>;
}
