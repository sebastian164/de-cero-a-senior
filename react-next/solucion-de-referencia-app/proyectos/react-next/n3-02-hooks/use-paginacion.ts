'use client';
// ---- react-next/nivel-03-patrones/02-hooks-personalizados/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
import { useState } from 'react';

interface Paginacion<T> {
  pagina: number;
  totalPaginas: number;
  elementos: readonly T[];
  siguiente: () => void;
  anterior: () => void;
  irA: (pagina: number) => void;
}

export function usePaginacion<T>(todos: readonly T[], porPagina = 10): Paginacion<T> {
  const [pagina, setPagina] = useState(1);
  const totalPaginas = Math.max(1, Math.ceil(todos.length / porPagina));
  const actual = Math.min(pagina, totalPaginas);         // si la lista se acorta, no queda fuera de rango

  const irA = (destino: number) => setPagina(Math.min(Math.max(1, destino), totalPaginas));

  return {
    pagina: actual,
    totalPaginas,
    elementos: todos.slice((actual - 1) * porPagina, actual * porPagina),
    siguiente: () => irA(actual + 1),
    anterior: () => irA(actual - 1),
    irA,
  };
}
