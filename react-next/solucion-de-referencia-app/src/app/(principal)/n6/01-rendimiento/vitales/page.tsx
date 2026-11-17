import { ReporteDeVitales } from '@app/n6-01/vitales';

export const metadata = { title: 'Métricas web en campo' };
export default function Pagina() {
  return <main><h1>Métricas web en campo</h1><ReporteDeVitales /><section><h2>Contenido de prueba</h2><p>Texto para que haya algo que medir.</p><button type="button">Pulsar</button></section></main>;
}
