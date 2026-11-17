'use client';
// Contexto de nivel-06/01: la tabla pesada (sus renderizados se cuentan con <Profiler> en las pruebas), el buscador con su propio estado y los productos.
import { useState } from 'react';

export interface Producto { readonly id: number; readonly nombre: string; readonly categoria: string }
export const PRODUCTOS: readonly Producto[] = Array.from({ length: 2000 }, (_, i) => ({
  id: i + 1, nombre: `Producto ${i + 1} ${['café', 'té', 'cacao', 'panela'][i % 4]}`, categoria: ['bebidas', 'despensa'][i % 2]!,
}));

/** Trabajo artificial: ~0,1 ms por fila, para que volver a renderizar se note. */
function costoso(n: number) { let x = 0; for (let i = 0; i < 2000; i++) x += Math.sqrt(i * n); return x; }

export function TablaPesada() {
  return (
    <table aria-label="Tabla pesada"><tbody>
      {PRODUCTOS.slice(0, 300).map((p) => <tr key={p.id}><td>{p.nombre}</td><td>{Math.round(costoso(p.id)) % 7}</td></tr>)}
    </tbody></table>
  );
}

export function Buscador() {
  const [texto, setTexto] = useState('');
  return <input aria-label="Buscar" value={texto} onChange={(e) => setTexto(e.target.value)} />;
}

export function filtrar(productos: readonly Producto[], texto: string): readonly Producto[] {
  const t = texto.toLowerCase();
  return productos.filter((p) => { for (let k = 0; k < 12; k++) costoso(p.id % 50 + k); return p.nombre.toLowerCase().includes(t); });
}
export const EsqueletoGrafica = () => <div className="esqueleto" style={{ height: 160 }} aria-hidden="true" />;
