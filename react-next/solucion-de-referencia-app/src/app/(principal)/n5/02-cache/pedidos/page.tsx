import { Suspense } from 'react';
import { obtenerUsuarioActual } from '@/react-next/n5-02-datos-cache/usuario-actual';
import { listarPedidosDe } from '@/react-next/n5-02-datos-cache/contexto';
import { ListaPedidos } from '@/react-next/n5-02-datos-cache/lista-pedidos';
import { AvisoDeError } from '@app/aviso';

export const metadata = { title: 'nivel-05/02 · Pedidos y mutaciones' };

async function Pedidos() {
  const usuario = await obtenerUsuarioActual();
  if (!usuario) return <p>Debes iniciar sesión (cookie usuario-demo).</p>;
  const pedidos = await listarPedidosDe(usuario.id);
  return <><AvisoDeError /><ListaPedidos pedidos={pedidos} /></>;
}
export default function Pagina() {
  return <main><h1>Mis pedidos</h1><Suspense fallback={<p>Cargando…</p>}><Pedidos /></Suspense></main>;
}
