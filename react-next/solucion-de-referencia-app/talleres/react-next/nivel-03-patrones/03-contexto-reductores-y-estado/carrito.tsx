'use client';
// Punto 2: proveedor con persistencia y cupones. La API pública (useCarrito, useAccionesDeCarrito) conserva la forma
// de la del README, así que los componentes de componentes.tsx no cambian al agregar estas capacidades.
import { z } from 'zod';
import { createContext, useContext, useEffect, useMemo, useReducer, useRef } from 'react';
import { totalCarrito, type Linea, type Producto } from '@/react-next/n3-03-carrito/reductor';
import { ESTADO_INICIAL, descuentoCupon, reductorAmpliado, type AccionAmpliada, type EstadoAmpliado } from './reductor-ampliado';

const ContextoEstado = createContext<EstadoAmpliado | null>(null);
const ContextoDespacho = createContext<React.Dispatch<AccionAmpliada> | null>(null);

export interface Persistencia {
  leer(): readonly Linea[] | null;
  guardar(lineas: readonly Linea[]): void;
}

const EsquemaLineas = z.array(z.object({
  productoId: z.string().min(1),
  nombre: z.string(),
  precio: z.number().nonnegative(),
  cantidad: z.number().int().positive(),
}));

export const persistenciaLocal: Persistencia = {
  leer: () => {
    try {
      const t = localStorage.getItem('carrito-n3');
      if (!t) return null;
      // Lo que viene de localStorage es entrada externa (otra versión, otra pestaña, manipulación): se valida en la frontera.
      const r = EsquemaLineas.safeParse(JSON.parse(t));
      return r.success ? r.data : null;
    } catch {
      return null;
    }
  },
  guardar: (l) => {
    try {
      localStorage.setItem('carrito-n3', JSON.stringify(l));
    } catch {
      /* sin espacio o bloqueado */
    }
  },
};

export function ProveedorDeCarrito({ children, persistencia = persistenciaLocal }: { children: React.ReactNode; persistencia?: Persistencia }) {
  const [estado, despachar] = useReducer(reductorAmpliado, ESTADO_INICIAL);
  const restaurado = useRef(false);
  // Sincronizar con un sistema externo (localStorage) sí es trabajo de un efecto. El reductor sigue puro.
  useEffect(() => {
    const lineas = persistencia.leer();
    if (lineas) despachar({ tipo: 'restaurado', lineas });
    restaurado.current = true;
  }, [persistencia]);
  useEffect(() => {
    if (restaurado.current) persistencia.guardar(estado.lineas);
  }, [estado.lineas, persistencia]);
  return (
    <ContextoDespacho value={despachar}>
      <ContextoEstado value={estado}>{children}</ContextoEstado>
    </ContextoDespacho>
  );
}

export function useCarrito() {
  const estado = useContext(ContextoEstado);
  if (!estado) throw new Error('Falta ProveedorDeCarrito');
  const descuento = descuentoCupon(estado);
  return {
    lineas: estado.lineas,
    total: totalCarrito(estado) - descuento,
    descuento,
    cupon: estado.cupon?.codigo ?? null,
    errorCupon: estado.errorCupon,
    cantidad: estado.lineas.length,
  };
}

export function useAccionesDeCarrito() {
  const despachar = useContext(ContextoDespacho);
  if (!despachar) throw new Error('Falta ProveedorDeCarrito');
  return useMemo(
    () => ({
      agregar: (producto: Producto) => despachar({ tipo: 'agregado', producto }),
      quitar: (productoId: string) => despachar({ tipo: 'quitado', productoId }),
      vaciar: () => despachar({ tipo: 'vaciado' }),
      aplicarCupon: (codigo: string) => despachar({ tipo: 'cuponAplicado', codigo }),
    }),
    [despachar],
  );
}
