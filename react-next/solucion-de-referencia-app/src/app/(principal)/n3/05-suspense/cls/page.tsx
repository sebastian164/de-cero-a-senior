import { Suspense } from 'react';
import { BloqueAlto, EsqueletoAlto, Giratorio } from '@talleres/react-next/nivel-03-patrones/05-suspense-limites-de-error-y-patrones-heredados/secciones-servidor';

export const metadata = { title: 'nivel-03/05 · CLS' };

async function Contenido({ modo }: { modo: Promise<string | undefined> }) {
  const m = (await modo) === 'esqueleto' ? 'esqueleto' : 'giratorio';
  return (
    <>
      <p>Modo: {m}</p>
      <Suspense fallback={m === 'esqueleto' ? <EsqueletoAlto /> : <Giratorio />}><BloqueAlto /></Suspense>
      <p>Texto que queda debajo del bloque y se mueve si el espacio no estaba reservado.</p>
    </>
  );
}

export default function Pagina({ searchParams }: { searchParams: Promise<{ modo?: string }> }) {
  return (
    <main>
      <h1>Salto de diseño: giratorio frente a esqueleto</h1>
      <Suspense fallback={null}><Contenido modo={searchParams.then((s) => s.modo)} /></Suspense>
    </main>
  );
}
