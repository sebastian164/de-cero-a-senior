'use client';
// Taller nivel-06/01, punto 6: el mismo hijo costoso con memorización manual y sin ella. Se cuenta cuántas veces se vuelve a renderizar (en un efecto sin dependencias).
import { memo, useCallback, useEffect, useMemo, useState } from 'react';

declare global {
  interface Window {
    __renders?: Record<string, number>;
  }
}
const contar = (clave: string) => {
  window.__renders ??= {};
  window.__renders[clave] = (window.__renders[clave] ?? 0) + 1;
};

function HijoBase({ datos, alPulsar, nombre }: { datos: readonly number[]; alPulsar: () => void; nombre: string }) {
  useEffect(() => contar(nombre));
  return (
    <button type="button" onClick={alPulsar}>
      {nombre}: {datos.length} datos
    </button>
  );
}
const HijoMemo = memo(HijoBase);

/** A mano: memo, useMemo y useCallback. */
export function ConMemorizacionManual() {
  const [tics, setTics] = useState(0);
  const datos = useMemo(() => [1, 2, 3], []);
  const alPulsar = useCallback(() => {}, []);
  return (
    <div>
      <button type="button" onClick={() => setTics((t) => t + 1)}>
        Tic manual {tics}
      </button>{' '}
      <HijoMemo datos={datos} alPulsar={alPulsar} nombre="manual" />
    </div>
  );
}

/** Sin nada: lo que el compilador de React debería poder memorizar por su cuenta. */
export function SinMemorizacion() {
  const [tics, setTics] = useState(0);
  const datos = [1, 2, 3];
  const alPulsar = () => {};
  return (
    <div>
      <button type="button" onClick={() => setTics((t) => t + 1)}>
        Tic simple {tics}
      </button>{' '}
      <HijoBase datos={datos} alPulsar={alPulsar} nombre="simple" />
    </div>
  );
}
