'use client';
// Taller nivel-06/01, punto 5: un filtro costoso sobre 2000 productos, sin y con useDeferredValue.
import { useMemo, useState } from 'react';
import { PRODUCTOS, filtrar } from '@/react-next/n6-01-rendimiento/contexto';
import { ListaFiltrada } from '@/react-next/n6-01-rendimiento/diferido';

export function ListaSinDiferir() {
  const [texto, setTexto] = useState('');
  const resultados = useMemo(() => filtrar(PRODUCTOS, texto), [texto]);
  return (
    <div>
      <input aria-label="Filtro" value={texto} onChange={(e) => setTexto(e.target.value)} />
      <ul data-testid="resultados">
        {resultados.map((p) => (
          <li key={p.id}>{p.nombre}</li>
        ))}
      </ul>
    </div>
  );
}
export { ListaFiltrada };
