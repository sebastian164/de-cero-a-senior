'use client';
// Punto 6: una lista costosa bajo un padre que se renderiza seguido. Se mide sin memorización y con memo.
import { memo, useState } from 'react';

export const FILAS = Array.from({ length: 2000 }, (_, i) => ({ id: i, texto: `Fila ${i}` }));

function trabajoCostoso(n: number) {
  let x = 0;
  for (let i = 0; i < 200; i++) x += Math.sqrt(n * i);
  return x;
}

export function ListaCostosa({ filas }: { filas: typeof FILAS }) {
  return <ul>{filas.map((f) => <li key={f.id}>{f.texto} · {trabajoCostoso(f.id).toFixed(0)}</li>)}</ul>;
}
export const ListaCostosaMemo = memo(ListaCostosa);

/** El reloj cambia cada vez que se pulsa; la lista no depende de él. */
export function PanelConReloj({ memorizar }: { memorizar: boolean }) {
  const [tics, setTics] = useState(0);
  const Lista = memorizar ? ListaCostosaMemo : ListaCostosa;
  return (
    <div>
      <button type="button" onClick={() => setTics((t) => t + 1)}>Tic {tics}</button>
      <Lista filas={FILAS} />
    </div>
  );
}
