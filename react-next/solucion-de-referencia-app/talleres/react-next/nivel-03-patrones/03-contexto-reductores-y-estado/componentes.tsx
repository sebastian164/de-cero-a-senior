'use client';
// Componentes del carrito. Usan solo la API pública del proveedor.
import { useState } from 'react';
import type { Producto } from '@/react-next/n3-03-carrito/reductor';
import { useAccionesDeCarrito, useCarrito } from './carrito';

export const renders = { boton: 0, resumen: 0 };
const moneda = (v: number) => '$' + v.toLocaleString('es-CO');

export function BotonAgregar({ producto }: { producto: Producto }) {
  const { agregar } = useAccionesDeCarrito();
  // eslint-disable-next-line react-hooks/immutability -- contador de renderizados: es justo lo que el taller mide (la alternativa sin mutar es <Profiler onRender>, ver nivel-03/03 punto 4)
  renders.boton++;
  return <button type="button" onClick={() => agregar(producto)}>Agregar {producto.nombre}</button>;
}

export function ResumenCarrito() {
  const { lineas, total, descuento, cantidad } = useCarrito();
  const { quitar, vaciar } = useAccionesDeCarrito();
  // eslint-disable-next-line react-hooks/immutability -- contador de renderizados: es justo lo que el taller mide (la alternativa sin mutar es <Profiler onRender>, ver nivel-03/03 punto 4)
  renders.resumen++;
  if (cantidad === 0) return <p>El carrito está vacío.</p>;
  return (
    <div>
      <ul>
        {lineas.map((l) => (
          <li key={l.productoId}>
            {l.nombre} × {l.cantidad} <button type="button" onClick={() => quitar(l.productoId)}>Quitar {l.nombre}</button>
          </li>
        ))}
      </ul>
      {descuento > 0 && <p>Descuento: {moneda(descuento)}</p>}
      <p>Total: <output aria-label="total">{moneda(total)}</output></p>
      <button type="button" onClick={vaciar}>Vaciar</button>
    </div>
  );
}

export function CampoCupon() {
  const [codigo, setCodigo] = useState('');
  const { aplicarCupon } = useAccionesDeCarrito();
  const { cupon, errorCupon } = useCarrito();
  return (
    <form onSubmit={(e) => { e.preventDefault(); aplicarCupon(codigo); }}>
      <label>Cupón <input value={codigo} onChange={(e) => setCodigo(e.target.value)} /></label> <button type="submit">Aplicar</button>
      {cupon && <p role="status">Cupón {cupon} aplicado</p>}
      {errorCupon && <p role="alert">{errorCupon}</p>}
    </form>
  );
}
