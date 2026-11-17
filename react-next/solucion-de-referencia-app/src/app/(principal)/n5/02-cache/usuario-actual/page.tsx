import { Suspense } from 'react';
import { obtenerUsuarioActual } from '@/react-next/n5-02-datos-cache/usuario-actual';
import { buscarUsuario, leerSesion } from '@/react-next/n5-02-datos-cache/contexto';

// Taller punto 5: tres componentes piden el usuario actual en la misma petición.
async function Cabecera() { const u = await obtenerUsuarioActual(); return <p>Cabecera: {u?.nombre}</p>; }
async function Cuerpo() { const u = await obtenerUsuarioActual(); return <p>Cuerpo: {u?.nombre}</p>; }
async function Pie() { const u = await obtenerUsuarioActual(); return <p>Pie: {u?.nombre} <span data-testid="consulta">consulta nº {u?.consulta}</span></p>; }
// Para comparar: la misma lectura sin `cache`.
async function SinCache() { const s = await leerSesion(); const u = s && await buscarUsuario(s.usuarioId); return <p data-testid="sin-cache">Sin cache: consulta nº {u?.consulta}</p>; }

async function Contenido({ modo }: { modo: string }) {
  if (modo === 'sin-cache') return <><SinCache /><SinCache /><SinCache /></>;
  return <><Cabecera /><Cuerpo /><Pie /></>;
}
export default async function Pagina({ searchParams }: { searchParams: Promise<{ modo?: string }> }) {
  const { modo = 'con-cache' } = await searchParams;
  return <main><h1>Usuario actual por petición</h1><Suspense fallback={<p>Cargando…</p>}><Contenido modo={modo} /></Suspense></main>;
}
