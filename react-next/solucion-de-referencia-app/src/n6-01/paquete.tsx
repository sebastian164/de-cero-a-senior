'use client';
// Taller nivel-06/01, punto 1. "Antes": todo es de cliente; la biblioteca de Markdown y la gráfica viajan en el paquete inicial.
import { marked } from 'marked';
import GraficaDeVentas from '@/react-next/n6-01-rendimiento/grafica-de-ventas';

const MD = '## Informe de ventas\n\nResumen **mensual** con cifras de la semana.\n\n- Café\n- Cacao\n- Panela';

export function PaginaAntes() {
  return (
    <main>
      <h1>Paquete: antes (todo en el cliente)</h1>
      <div dangerouslySetInnerHTML={{ __html: marked.parse(MD, { async: false }) }} />
      <GraficaDeVentas />
    </main>
  );
}
