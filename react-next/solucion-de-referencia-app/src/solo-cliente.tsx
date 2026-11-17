'use client';
// react-hook-form genera los ids de useFieldArray con performance.now()/Math.random(). Con Cache Components, leerlos durante el
// prerender aborta TODO el prerender de la página y los demás formularios (con acción de servidor) dejan de salir en el HTML
// (sin JavaScript no habría formulario que enviar). Los formularios que necesitan JavaScript se montan solo en el navegador.
import dynamic from 'next/dynamic';

export const FormularioClinicaBibliotecaSoloCliente = dynamic(
  () => import('@talleres/react-next/nivel-04-clean-code-y-testing/02-formularios-y-validacion/formulario-biblioteca').then((m) => m.FormularioClinicaBiblioteca),
  { ssr: false, loading: () => <p>Cargando formulario…</p> },
);
