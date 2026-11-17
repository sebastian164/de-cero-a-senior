'use client';
// Contexto: componente de cliente que recibe contenido de servidor como children.
import { useState } from 'react';

export function PanelDesplegable({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  const [abierto, setAbierto] = useState(false);
  return (
    <section>
      <button type="button" aria-expanded={abierto} onClick={() => setAbierto(!abierto)}>{abierto ? '▾' : '▸'} {titulo}</button>
      <div hidden={!abierto}>{children}</div>
    </section>
  );
}
