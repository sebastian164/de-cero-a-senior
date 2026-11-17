import Link from 'next/link';
import { obtenerUsuarioActual } from '@talleres/react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/sesion';
import { salir } from '@talleres/react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/acciones-ingreso';
import { FormularioIngreso } from '@app/n6-02/formulario-ingreso';

export const metadata = { title: 'nivel-06/02 · Seguridad' };

export default async function Pagina({ searchParams }: { searchParams: Promise<{ retorno?: string }> }) {
  const [{ retorno }, usuario] = await Promise.all([searchParams, obtenerUsuarioActual()]);
  return (
    <main>
      <h1>nivel-06/02 · Seguridad y autenticación</h1>
      {usuario ? (
        <>
          <p>Sesión de <b>{usuario.nombre}</b>. <Link href="/n6/02-seguridad/privado">Mis pedidos</Link></p>
          <form action={salir}><button type="submit">Salir</button></form>
        </>
      ) : <FormularioIngreso retorno={retorno} />}
    </main>
  );
}
