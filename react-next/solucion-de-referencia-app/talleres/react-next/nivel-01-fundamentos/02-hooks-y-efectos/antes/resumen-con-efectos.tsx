'use client';
// ANTES (mal a propósito, punto 3): tres efectos; solo uno sincroniza con algo externo. Los otros dos derivan estado en un efecto.
import { useEffect, useState } from 'react';
import type { Producto } from '../componentes';

/** Antes: tres efectos; solo uno sincroniza con algo externo (el título del documento). */
export function ResumenCarritoConEfectos({ productos, onCambio }: { productos: readonly Producto[]; onCambio: (n: number) => void }) {
  const [total, setTotal] = useState(0);
  const [cantidad, setCantidad] = useState(0);
  useEffect(() => { setTotal(productos.reduce((s, p) => s + p.precio, 0)); }, [productos]);   // derivado
  useEffect(() => { setCantidad(productos.length); onCambio(productos.length); }, [productos, onCambio]); // aviso al padre
  useEffect(() => { document.title = `Carrito (${productos.length})`; }, [productos.length]);  // externo: se queda
  return <p>{cantidad} productos · {total}</p>;
}
