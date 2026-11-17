import { Suspense } from 'react';
import { obtenerUsuarioActual } from '@/react-next/n2-04-isp/usuarios';
import { MenuCompleto } from '@talleres/react-next/nivel-02-solid/04-isp/menu-completo';

export const metadata = { title: 'nivel-02/04 · Sin proyectar' };

async function EncabezadoSinProyectar() {
  const usuario = await obtenerUsuarioActual();
  return <MenuCompleto usuario={usuario} />; // antes: el registro entero cruza al navegador
}

export default function Pagina() {
  return <main><h1>Usuario completo hacia el cliente (antes)</h1><Suspense fallback={<p>Cargando…</p>}><EncabezadoSinProyectar /></Suspense></main>;
}
