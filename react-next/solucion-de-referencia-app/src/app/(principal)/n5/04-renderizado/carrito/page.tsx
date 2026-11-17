import { Suspense } from 'react';
import { cookies } from 'next/headers';

async function Indicador() {
  const n = (await cookies()).get('carrito')?.value ?? '0';
  return <p>Artículos en el carrito: <span data-testid="n-carrito">{n}</span></p>;
}
export default function Pagina() {
  return <main><h1>Carrito</h1><p>Cáscara estática, con un hueco dinámico por usuario:</p><Suspense fallback={<p>Cargando carrito…</p>}><Indicador /></Suspense></main>;
}
