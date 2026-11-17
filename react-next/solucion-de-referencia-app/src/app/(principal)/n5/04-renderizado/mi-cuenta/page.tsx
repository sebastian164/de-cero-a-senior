import { Suspense } from 'react';
import type { Metadata } from 'next';
import { cookies } from 'next/headers';

export const metadata: Metadata = { title: 'Mi cuenta', robots: { index: false, follow: false } };

async function Cuenta() {
  const usuario = (await cookies()).get('usuario-demo')?.value;
  return <p>{usuario ? `Cuenta de ${usuario}` : 'Sin sesión'}</p>;
}
export default function Pagina() { return <main><h1>Mi cuenta</h1><Suspense fallback={<p>Cargando…</p>}><Cuenta /></Suspense></main>; }
