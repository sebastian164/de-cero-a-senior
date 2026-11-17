import { Suspense } from 'react';
import { Bloque } from '@app/bloque';
import { FormularioRegistro } from '@/react-next/n4-02-formularios/registro/formulario';
import { FormularioRegistroEnVivo } from '@/react-next/n4-02-formularios/registro/formulario-en-vivo';
import { FormularioClinicaAccion } from '@talleres/react-next/nivel-04-clean-code-y-testing/02-formularios-y-validacion/formulario-accion';
import { FormularioClinicaBibliotecaSoloCliente } from '@app/solo-cliente';

export const metadata = { title: 'nivel-04/02 · Formularios' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-04/02 · Formularios y validación</h1>
      <Bloque titulo="README: registro con acción de servidor (valida al enviar)" origen="proyectos/react-next/n4-02-formularios/registro/formulario.tsx">
        <Suspense fallback={<p>Cargando formulario…</p>}><FormularioRegistro /></Suspense>
      </Bloque>
      <Bloque titulo="README: registro con biblioteca (valida al salir del campo)" origen="proyectos/react-next/n4-02-formularios/registro/formulario-en-vivo.tsx">
        <Suspense fallback={<p>Cargando formulario…</p>}><FormularioRegistroEnVivo /></Suspense>
      </Bloque>
      <Bloque titulo="Taller: ficha de clínica con acción de servidor" origen="talleres/…/02-formularios-y-validacion/formulario-accion.tsx">
        <Suspense fallback={<p>Cargando formulario…</p>}><FormularioClinicaAccion /></Suspense>
      </Bloque>
      <Bloque titulo="Taller: ficha de clínica con biblioteca" origen="talleres/…/02-formularios-y-validacion/formulario-biblioteca.tsx">
        <Suspense fallback={<p>Cargando formulario…</p>}><FormularioClinicaBibliotecaSoloCliente /></Suspense>
      </Bloque>
    </main>
  );
}
