'use client';
// ---- react-next/nivel-02-solid/05-dip/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
import { createContext, useContext } from 'react';
import { agregarAlCarrito, type Dependencias, type Producto } from './carrito';
import { almacenLocal, analiticaGoogle } from './implementaciones';

// carrito/dependencias.tsx — inyección por contexto
const ContextoDependencias = createContext<Dependencias | null>(null);

export function ProveedorDeDependencias({ valor, children }: { valor: Dependencias; children: React.ReactNode }) {
  return <ContextoDependencias value={valor}>{children}</ContextoDependencias>;
}

export function useDependencias(): Dependencias {
  const dependencias = useContext(ContextoDependencias);
  if (!dependencias) throw new Error('Falta ProveedorDeDependencias');
  return dependencias;
}

// En el componente
function BotonAgregar({ producto }: { producto: Producto }) {
  const dependencias = useDependencias();
  return <button type="button" onClick={() => agregarAlCarrito(producto, dependencias)}>Agregar</button>;
}

// Ensamblaje real, una vez, en la raíz de la parte de cliente
<ProveedorDeDependencias valor={{ almacen: almacenLocal, analitica: analiticaGoogle, ahora: Date.now }}>
  <BotonAgregar producto={{ id: 'p1', nombre: 'Café', precio: 18000 }} />
</ProveedorDeDependencias>;

export { BotonAgregar };
