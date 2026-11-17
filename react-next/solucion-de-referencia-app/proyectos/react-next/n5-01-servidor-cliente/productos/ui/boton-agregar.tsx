// ---- react-next/nivel-05-arquitectura/01-componentes-de-servidor-y-de-cliente/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
// productos/ui/boton-agregar.tsx
'use client';
export function BotonAgregarAlCarrito({ productoId, precio }: { productoId: string; precio: number }) {
  const { agregar } = useAccionesDeCarrito();
  return <button type="button" onClick={() => agregar({ productoId, precio })}>Agregar al carrito</button>;
}

import { useAccionesDeCarrito } from '../../acciones-carrito';
