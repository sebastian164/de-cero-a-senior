import { Bloque } from '@app/bloque';
import { UsoPestanas } from '@/react-next/n3-01-composicion/uso-pestanas';
import { PanelConHistorial } from '@/react-next/n3-01-composicion/panel-con-historial';
import { DemoComposicion } from '@talleres/react-next/nivel-03-patrones/01-composicion-y-componentes-compuestos/demo';

export const metadata = { title: 'nivel-03/01 · Composición' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-03/01 · Composición y componentes compuestos</h1>
      <Bloque titulo="Ejemplo del README: pestañas" origen="proyectos/react-next/n3-01-composicion/">
        <UsoPestanas pendientes={2} />
      </Bloque>
      <Bloque titulo="Ejemplo del README: componente de cliente con contenido de servidor como children" origen="proyectos/react-next/n3-01-composicion/panel-con-historial.tsx">
        <PanelConHistorial id="C-7" />
      </Bloque>
      <Bloque titulo="Taller: pestañas con teclado, acordeones y diálogo nativo" origen="talleres/react-next/nivel-03-patrones/01-composicion-y-componentes-compuestos/">
        <DemoComposicion />
      </Bloque>
    </main>
  );
}
