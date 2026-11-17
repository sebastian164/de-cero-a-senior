'use client';
// ...y luego a un hook delgado que conecta esa lógica con el estado de React y la paginación del README.
import { useState } from 'react';
import { usePaginacion } from '@/react-next/n3-02-hooks/use-paginacion';
import { filtrar, ordenar, type Fila, type Orden } from './tabla';

export function useTabla(filas: readonly Fila[], porPagina = 3) {
  const [texto, setTexto] = useState('');
  const [orden, setOrden] = useState<Orden>({ campo: 'cliente', sentido: 'asc' });
  const visibles = ordenar(filtrar(filas, texto), orden);
  const paginacion = usePaginacion(visibles, porPagina);
  const ordenarPor = (campo: Orden['campo']) =>
    setOrden((o) => ({ campo, sentido: o.campo === campo && o.sentido === 'asc' ? 'desc' : 'asc' }));
  return { texto, setTexto, orden, ordenarPor, ...paginacion };
}
