// ---- react-next/nivel-03-patrones/03-contexto-reductores-y-estado/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
// carrito/proveedor.tsx
'use client';

const ContextoEstado = createContext<EstadoCarrito | null>(null);
const ContextoDespacho = createContext<React.Dispatch<AccionCarrito> | null>(null);

export function ProveedorDeCarrito({ children }: { children: React.ReactNode }) {
  const [estado, despachar] = useReducer(reductorCarrito, { lineas: [] });
  return (
    <ContextoDespacho value={despachar}>
      <ContextoEstado value={estado}>{children}</ContextoEstado>
    </ContextoDespacho>
  );
}

// API pública: los componentes no conocen el reductor ni las acciones
export function useCarrito() {
  const estado = useContext(ContextoEstado);
  if (!estado) throw new Error('Falta ProveedorDeCarrito');
  return { lineas: estado.lineas, total: totalCarrito(estado), cantidad: estado.lineas.length };
}

export function useAccionesDeCarrito() {
  const despachar = useContext(ContextoDespacho);
  if (!despachar) throw new Error('Falta ProveedorDeCarrito');
  return useMemo(() => ({
    agregar: (producto: Producto) => despachar({ tipo: 'agregado', producto }),
    quitar: (productoId: string) => despachar({ tipo: 'quitado', productoId }),
    vaciar: () => despachar({ tipo: 'vaciado' }),
  }), [despachar]);
}

import { createContext, useContext, useMemo, useReducer } from 'react';
import { reductorCarrito, totalCarrito, type AccionCarrito, type EstadoCarrito, type Producto } from './reductor';
