// ---- react-next/nivel-06-avanzado/01-rendimiento/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
'use client';
import dynamic from 'next/dynamic';
import { EsqueletoGrafica } from './contexto';

const Grafica = dynamic(() => import('./grafica-de-ventas'), {
  loading: () => <EsqueletoGrafica />,
  ssr: false,                                           // solo si depende del navegador
});

export { Grafica };
