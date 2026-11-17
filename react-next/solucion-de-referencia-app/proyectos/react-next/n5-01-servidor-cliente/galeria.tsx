'use client';
// Contexto: el carrusel, la única pieza de la ficha que necesita estado en el navegador.
import { useState } from 'react';

export function Galeria({ imagenes }: { imagenes: readonly string[] }) {
  const [actual, setActual] = useState(0);
  return (
    <figure className="galeria">
      {/* eslint-disable-next-line @next/next/no-img-element -- SVG locales de demostración, con dimensiones fijas */}
      <img src={imagenes[actual]} alt={`Imagen ${actual + 1} de ${imagenes.length}`} width={240} height={160} />
      <figcaption>
        <button type="button" onClick={() => setActual((i) => (i - 1 + imagenes.length) % imagenes.length)} aria-label="Imagen anterior">‹</button>
        {' '}{actual + 1}/{imagenes.length}{' '}
        <button type="button" onClick={() => setActual((i) => (i + 1) % imagenes.length)} aria-label="Imagen siguiente">›</button>
      </figcaption>
    </figure>
  );
}
