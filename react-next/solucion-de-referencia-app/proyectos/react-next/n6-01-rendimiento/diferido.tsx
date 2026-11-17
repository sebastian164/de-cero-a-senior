// ---- react-next/nivel-06-avanzado/01-rendimiento/README.md (bloque 2, desde 'const [texto'), copiado tal cual entre el contexto agregado ----
import { useDeferredValue, useMemo, useState } from 'react';
import { PRODUCTOS, filtrar } from './contexto';

export function ListaFiltrada() {
  const productos = PRODUCTOS;
const [texto, setTexto] = useState('');
const textoDiferido = useDeferredValue(texto);          // el campo responde ya; la lista, cuando pueda
const resultados = useMemo(() => filtrar(productos, textoDiferido), [productos, textoDiferido]);
  return (
    <div>
      <input aria-label="Filtro" value={texto} onChange={(e) => setTexto(e.target.value)} />
      <ul data-testid="resultados">{resultados.map((p) => <li key={p.id}>{p.nombre}</li>)}</ul>
    </div>
  );
}
