import { Suspense } from 'react';
import Link from 'next/link';
import { Bloque } from '@app/bloque';
import Encabezado from '@/react-next/n2-04-isp/encabezado';
import { Avatar, ProveedorDeTema } from '@/react-next/n2-04-isp/tema';

export const metadata = { title: 'nivel-02/04 · ISP' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-02/04 · ISP en React</h1>
      <Bloque titulo="Ejemplo del README: Encabezado proyecta dos campos antes de cruzar al cliente" origen="proyectos/react-next/n2-04-isp/encabezado.tsx">
        <Suspense fallback={<p>Cargando usuario…</p>}><Encabezado /></Suspense>
        <p><Link href="/n2/04-isp/sin-proyectar">Comparar con la versión que pasa el usuario completo</Link></p>
      </Bloque>
      <Bloque titulo="Ejemplo del README: Avatar y contexto de tema" origen="proyectos/react-next/n2-04-isp/tema.tsx">
        <ProveedorDeTema><Avatar src="/avatar.svg" alt="Avatar de ejemplo" /></ProveedorDeTema>
      </Bloque>
    </main>
  );
}
