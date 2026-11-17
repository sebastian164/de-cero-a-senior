'use client';
// ---- react-next/nivel-03-patrones/02-hooks-personalizados/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
import { useMemo } from 'react';

export interface Pedido { readonly id: string; readonly creadoEn: Date }

export function useOrdenado<T>(elementos: readonly T[], comparar: (a: T, b: T) => number) {
  return useMemo(() => elementos.toSorted(comparar), [elementos, comparar]);
}

// La estrategia se define fuera del componente para que su referencia sea estable
const porFechaDescendente = (a: Pedido, b: Pedido) => b.creadoEn.getTime() - a.creadoEn.getTime();
export function ListaOrdenada({ pedidos }: { pedidos: readonly Pedido[] }) {   // contexto: el uso va dentro de un componente
  const ordenados = useOrdenado(pedidos, porFechaDescendente);
  return <ol>{ordenados.map((p) => <li key={p.id}>{p.id}</li>)}</ol>;
}
