import { Suspense } from 'react';
import { cookies } from 'next/headers';
import { fijarUsuarioOculto, pedidosBien, pedidosMal } from './datos';

async function Pedidos({ modo }: { modo: 'mal' | 'bien' }) {
  const usuarioId = (await cookies()).get('usuario-demo')?.value ?? 'nadie';
  const pedidos = modo === 'mal' ? (fijarUsuarioOculto(usuarioId), await pedidosMal()) : await pedidosBien(usuarioId);
  return <p>Pedidos de <b>{usuarioId}</b> ({modo}): <span data-testid="pedidos">{pedidos.join(', ')}</span></p>;
}
export default async function Pagina({ searchParams }: { searchParams: Promise<{ modo?: string }> }) {
  const { modo } = await searchParams;
  return <main><h1>Datos por usuario</h1><Suspense fallback={<p>Cargando…</p>}><Pedidos modo={modo === 'mal' ? 'mal' : 'bien'} /></Suspense></main>;
}
