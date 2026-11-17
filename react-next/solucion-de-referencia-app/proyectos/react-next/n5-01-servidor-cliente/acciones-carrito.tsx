'use client';
// Contexto: useAccionesDeCarrito con la forma que usa el botón del README ({ productoId, precio }).
import { createContext, useContext, useMemo, useState } from 'react';

export interface LineaAgregada { readonly productoId: string; readonly precio: number }
const Acciones = createContext<{ agregar(linea: LineaAgregada): void } | null>(null);
const Cantidad = createContext(0);

export function ProveedorCarritoProducto({ children }: { children: React.ReactNode }) {
  const [lineas, setLineas] = useState<readonly LineaAgregada[]>([]);
  const acciones = useMemo(() => ({ agregar: (l: LineaAgregada) => setLineas((ls) => [...ls, l]) }), []);
  return <Acciones.Provider value={acciones}><Cantidad.Provider value={lineas.length}>{children}</Cantidad.Provider></Acciones.Provider>;
}
export function useAccionesDeCarrito() {
  const acciones = useContext(Acciones);
  if (!acciones) throw new Error('useAccionesDeCarrito necesita <ProveedorCarritoProducto>');
  return acciones;
}
export function IndicadorCarrito() {
  return <p role="status">En el carrito: {useContext(Cantidad)}</p>;
}
