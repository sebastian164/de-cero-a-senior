import { Suspense } from 'react';
import Image from 'next/image';
import localFont from 'next/font/local';

const geist = localFont({ src: '../../../../../fuentes/geist-latin.woff2', display: 'swap', variable: '--fuente-geist' });

// modo=optimizado: next/image (tamaños adaptados, formatos modernos, dimensiones reservadas) y next/font (autoalojada, sin salto).
// modo=simple: <img> sin dimensiones y una fuente cargada con @font-face tardío.
async function Contenido({ searchParams }: { searchParams: Promise<{ modo?: string }> }) {
  const { modo = 'optimizado' } = await searchParams;
  if (modo === 'simple') {
    return (
      <main style={{ fontFamily: '"Geist Tardía", system-ui' }}>
        <style>{'@font-face{font-family:"Geist Tardía";src:url(/geist-latin.woff2) format("woff2");font-display:swap}'}</style>
        <h1>Portada (sin optimizar)</h1>
        {/* eslint-disable-next-line @next/next/no-img-element -- es la versión "sin optimizar" del taller */}
        <img src="/portada.png" alt="Portada de la tienda" style={{ maxWidth: '100%' }} />
        <p>Texto bajo la imagen.</p>
      </main>
    );
  }
  return (
    <main className={geist.className}>
      <h1>Portada (optimizada)</h1>
      <Image src="/portada.png" alt="Portada de la tienda" width={1600} height={900} sizes="(max-width: 980px) 100vw, 980px" priority style={{ maxWidth: '100%', height: 'auto' }} />
      <p>Texto bajo la imagen.</p>
    </main>
  );
}

export default function Pagina({ searchParams }: { searchParams: Promise<{ modo?: string }> }) {
  return <Suspense fallback={<p>Cargando…</p>}><Contenido searchParams={searchParams} /></Suspense>;
}
