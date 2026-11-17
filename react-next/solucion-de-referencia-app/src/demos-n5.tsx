'use client';
// Taller nivel-05/01, punto 2: un componente de cliente que recibe el registro completo (todo lo que reciba llega al navegador).
import type { Producto } from '@/react-next/n5-01-servidor-cliente/contexto';

export function FichaDeCliente({ producto }: { producto: Pick<Producto, 'nombre'> & Partial<Producto> }) {
  return <p>Ficha de cliente: {producto.nombre}</p>;
}
