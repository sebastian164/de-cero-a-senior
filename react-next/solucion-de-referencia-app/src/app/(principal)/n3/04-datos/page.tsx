import Link from 'next/link';
import { Bloque } from '@app/bloque';
import { ContadorPendientes, ListaCancelable } from '@talleres/react-next/nivel-03-patrones/04-capa-de-datos-y-adaptadores/ui';

export const metadata = { title: 'nivel-03/04 · Capa de datos' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-03/04 · Capa de datos y adaptadores</h1>
      <p><Link href="/n3/04-datos/servidor?estado=1">Ejemplo del README: consumo en el servidor con searchParams</Link></p>
      <Bloque titulo="Taller: consulta, mutación optimista con reversa y deduplicación" origen="talleres/…/04-capa-de-datos-y-adaptadores/ui.tsx">
        <ContadorPendientes />
        <ListaCancelable />
      </Bloque>
    </main>
  );
}
