'use client';
// Taller nivel-06/01, punto 4: 10.000 filas, sin y con virtualización (@tanstack/react-virtual).
import { useRef, useState } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';

const FILAS = Array.from({ length: 10_000 }, (_, i) => ({ id: i, texto: `Fila ${i} · producto de prueba ${i % 97}` }));

function medir(alMedir: (ms: number, nodos: number) => void) {
  const t0 = performance.now();
  requestAnimationFrame(() => requestAnimationFrame(() => alMedir(performance.now() - t0, document.querySelectorAll('li').length)));
}

export function FilasPrueba({ virtual }: { virtual: boolean }) {
  const [mostrar, setMostrar] = useState(false);
  const [resultado, setResultado] = useState<{ ms: number; nodos: number } | null>(null);
  return (
    <div>
      <button
        type="button"
        onClick={() => {
          setMostrar(true);
          medir((ms, nodos) => setResultado({ ms: Math.round(ms), nodos }));
        }}
      >
        Mostrar 10.000 filas
      </button>
      {resultado && (
        <p data-testid="resultado">
          {resultado.ms} ms · {resultado.nodos} nodos de fila
        </p>
      )}
      {mostrar &&
        (virtual ? (
          <Virtual />
        ) : (
          <ul data-testid="lista">
            {FILAS.map((f) => (
              <li key={f.id}>{f.texto}</li>
            ))}
          </ul>
        ))}
    </div>
  );
}

function Virtual() {
  const contenedor = useRef<HTMLDivElement>(null);
  // eslint-disable-next-line react-hooks/incompatible-library -- useVirtualizer devuelve funciones que cambian sin cambiar de identidad: el compilador de React omite este componente a propósito
  const v = useVirtualizer({ count: FILAS.length, getScrollElement: () => contenedor.current, estimateSize: () => 24, overscan: 8 });
  return (
    <div ref={contenedor} data-testid="lista" style={{ height: 360, overflow: 'auto', border: '1px solid var(--borde)' }}>
      <ul style={{ height: v.getTotalSize(), position: 'relative', margin: 0, padding: 0, listStyle: 'none' }}>
        {v.getVirtualItems().map((it) => (
          <li key={it.key} style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: it.size, transform: `translateY(${it.start}px)` }}>
            {FILAS[it.index]!.texto}
          </li>
        ))}
      </ul>
    </div>
  );
}
