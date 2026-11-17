'use client';
// Taller nivel-06/01, punto 3: dos casos resueltos sin memorización. Cada hijo pesado cuenta cuántas veces se renderiza.
import { useState, type ReactNode } from 'react';

export const contadores = { pesadoAntes: 0, pesadoDespuesEstado: 0, pesadoDespuesChildren: 0 };

// eslint-disable-next-line react-hooks/immutability -- contador de renderizados: es justo lo que el taller mide (la alternativa sin mutar es <Profiler onRender>, ver nivel-03/03 punto 4)
function Pesado({ clave }: { clave: keyof typeof contadores }) { contadores[clave]++; return <p>Contenido costoso</p>; }

// Caso 1 (bajar el estado). Antes: el estado del campo está en el padre y arrastra al hijo pesado.
export function PaginaAntes() {
  const [texto, setTexto] = useState('');
  return <><input aria-label="Campo" value={texto} onChange={(e) => setTexto(e.target.value)} /><Pesado clave="pesadoAntes" /></>;
}
// Después: el estado vive en el componente que lo usa.
function Campo() {
  const [texto, setTexto] = useState('');
  return <input aria-label="Campo" value={texto} onChange={(e) => setTexto(e.target.value)} />;
}
export function PaginaDespuesEstado() { return <><Campo /><Pesado clave="pesadoDespuesEstado" /></>; }

// Caso 2 (children). Un componente con estado envuelve contenido costoso que recibe ya creado.
export function ContenedorConReloj({ children }: { children: ReactNode }) {
  const [tics, setTics] = useState(0);
  return <section><button type="button" onClick={() => setTics((t) => t + 1)}>Tic {tics}</button>{children}</section>;
}
export function PaginaDespuesChildren() { return <ContenedorConReloj><Pesado clave="pesadoDespuesChildren" /></ContenedorConReloj>; }
