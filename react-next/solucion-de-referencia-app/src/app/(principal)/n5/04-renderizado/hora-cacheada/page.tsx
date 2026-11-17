import { Suspense } from 'react';
import { horaCacheada } from '../datos';

async function Hora() {
  const h = await horaCacheada();
  return <p>Generada en <span data-testid="hora">{h.generadaEn}</span> por <span data-testid="instancia">{h.instancia}</span></p>;
}
export default function Pagina() { return <main><h1>Caché local de cada réplica</h1><Suspense fallback={<p>Cargando…</p>}><Hora /></Suspense></main>; }
