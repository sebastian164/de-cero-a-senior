// "Después": el Markdown se convierte en el servidor y la gráfica se carga de forma diferida (bloque 3 del README).
import { marked } from 'marked';
import { Grafica } from '@/react-next/n6-01-rendimiento/dinamica';

export const metadata = { title: 'Paquete: después' };

const MD = '## Informe de ventas\n\nResumen **mensual** con cifras de la semana.\n\n- Café\n- Cacao\n- Panela';

export default function Pagina() {
  return (
    <main>
      <h1>Paquete: después (servidor + carga diferida)</h1>
      <div dangerouslySetInnerHTML={{ __html: marked.parse(MD, { async: false }) }} />
      <Grafica />
    </main>
  );
}
