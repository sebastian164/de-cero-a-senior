import { Bloque } from '@app/bloque';
import { DemoDip } from '@talleres/react-next/nivel-02-solid/05-dip/demo-cliente';
import { FormularioCancelar } from '@talleres/react-next/nivel-02-solid/05-dip/formulario-cancelar';
import { repositorio } from '@talleres/react-next/nivel-02-solid/05-dip/composicion';
import { connection } from 'next/server';
import { Suspense } from 'react';

export const metadata = { title: 'nivel-02/05 · DIP' };

async function Pedidos() {
  await connection();
  const pedidos = repositorio.todos();
  return (
    <>
      <ul>{pedidos.map((p) => <li key={p.id}>{p.id} · cliente {p.clienteId} · {p.estado}</li>)}</ul>
      <FormularioCancelar ids={pedidos.map((p) => p.id)} />
    </>
  );
}

export default function Pagina() {
  return (
    <main>
      <h1>nivel-02/05 · DIP en React</h1>
      <Bloque titulo="Ejemplo del README: BotonAgregar con dependencias por contexto" origen="proyectos/react-next/n2-05-dip/dependencias.tsx">
        <DemoDip />
      </Bloque>
      <Bloque titulo="Taller 4: caso de uso de servidor con módulo de composición" origen="talleres/react-next/nivel-02-solid/05-dip/composicion.ts">
        <Suspense fallback={<p>Cargando pedidos…</p>}><Pedidos /></Suspense>
      </Bloque>
    </main>
  );
}
